/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.openide.explorer.view;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.ListIterator;
import org.netbeans.junit.NbTestCase;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.nodes.NodeMemberEvent;

/** Identity, lazy-slot, fallback and deterministic batch-work contracts. */
public class VisualizerChildrenBatchTest extends NbTestCase {
    public VisualizerChildrenBatchTest(String name) { super(name); }

    @Override protected boolean runInEQ() { return true; }

    private static final class Snapshot extends ArrayList<Node> {
        int reads;
        Snapshot(int size) {
            for (int i = 0; i < size; i++) {
                add(new AbstractNode(Children.LEAF));
            }
        }
        @Override public Node get(int index) { reads++; return super.get(index); }
    }

    private static final class Fixture {
        final AbstractNode node = new AbstractNode(new Children.Array());
        final VisualizerNode parent = VisualizerNode.getVisualizer(null, node);
        final Snapshot initial;
        final VisualizerChildren children;
        Fixture(int size, boolean mixed) {
            initial = new Snapshot(size);
            children = new VisualizerChildren(parent, initial);
            parent.notifyVisualizerChildrenChange(true, children);
            if (mixed) {
                for (int i = 0; i < size; i += 2) { children.getChildAt(i); }
            }
        }
        NodeMemberEvent event(boolean add, Snapshot next) throws Exception {
            Constructor<NodeMemberEvent> c = NodeMemberEvent.class.getDeclaredConstructor(
                    Node.class, boolean.class, int[].class, List.class, List.class);
            c.setAccessible(true);
            return c.newInstance(node, add, new int[0], next, initial);
        }
        VisualizerEvent.Added add(int[] indices, Snapshot next) throws Exception {
            VisualizerEvent.Added e = new VisualizerEvent.Added(children, indices, event(true, next));
            children.added(e);
            return e;
        }
        VisualizerEvent.Removed remove(int[] indices, Snapshot next) throws Exception {
            VisualizerEvent.Removed e = new VisualizerEvent.Removed(children, indices, event(false, next));
            children.removed(e);
            return e;
        }
    }

    private static void identities(List<VisualizerNode> expected, List<VisualizerNode> actual) {
        assertEquals(expected.size(), actual.size());
        for (int i = 0; i < expected.size(); i++) { assertSame("slot " + i, expected.get(i), actual.get(i)); }
    }

    public void testSmallDomainDifferentialBatchesPreserveIdentityAndLazySlots() throws Exception {
        for (int size = 0; size <= 7; size++) {
            for (int mask = 0; mask < (1 << size); mask++) {
                int[] indices = new int[Integer.bitCount(mask)];
                int w = 0;
                for (int i = 0; i < size; i++) { if ((mask & (1 << i)) != 0) { indices[w++] = i; } }
                Fixture f = new Fixture(size - indices.length, true);
                List<VisualizerNode> slots = f.children.getVisNodes(false);
                List<VisualizerNode> expected = new ArrayList<>(slots);
                for (int i : indices) { expected.add(i, null); }
                Snapshot next = new Snapshot(size);
                f.add(indices, next);
                assertSame(slots, f.children.getVisNodes(false));
                identities(expected, slots);
                assertEquals("addition never materializes snapshot entries", 0, next.reads);
                for (int i = 0; i < slots.size(); i++) {
                    if (slots.get(i) != null) { assertEquals(i, f.children.getIndex(slots.get(i))); }
                }

                f = new Fixture(size, true);
                slots = f.children.getVisNodes(false);
                expected = new ArrayList<>(slots);
                List<VisualizerNode> payload = new ArrayList<>();
                for (int i = indices.length - 1; i >= 0; i--) {
                    VisualizerNode v = expected.remove(indices[i]);
                    payload.add(v == null ? VisualizerNode.EMPTY : v);
                }
                next = new Snapshot(size - indices.length);
                VisualizerEvent.Removed e = f.remove(indices, next);
                assertSame(slots, f.children.getVisNodes(false));
                identities(expected, slots);
                identities(payload, e.removed);
                assertEquals("removal never materializes snapshot entries", 0, next.reads);
            }
        }
    }

    public void testLazyMaterializationOccursOnlyOnExplicitAccess() throws Exception {
        Fixture f = new Fixture(4, true);
        assertEquals("two initial nodes created", 2, f.initial.reads);
        Snapshot next = new Snapshot(6);
        f.add(new int[]{0, 3}, next);
        assertEquals(0, next.reads);
        VisualizerNode created = (VisualizerNode) f.children.getChildAt(0);
        assertEquals("one newly requested node", 1, next.reads);
        assertSame(created, f.children.getChildAt(0));
        assertEquals("cached identity", 1, next.reads);
        assertEquals(0, f.children.getIndex(created));
        Snapshot afterRemoval = new Snapshot(4);
        f.remove(new int[]{1, 4}, afterRemoval);
        assertEquals(0, afterRemoval.reads);
        assertSame(created, f.children.getChildAt(0));
        assertEquals(0, afterRemoval.reads);
    }

    public void testAncestorListenerTraversalUsesSameEvent() throws Exception {
        Fixture f = new Fixture(3, true);
        Children.Array ancestorNodes = new Children.Array();
        AbstractNode ancestorNode = new AbstractNode(ancestorNodes);
        ancestorNodes.add(new Node[]{f.node});
        VisualizerNode ancestor = VisualizerNode.getVisualizer(null, ancestorNode);
        VisualizerChildren ancestorChildren = new VisualizerChildren(ancestor, Arrays.<Node>asList(f.node));
        ancestor.notifyVisualizerChildrenChange(true, ancestorChildren);
        assertSame(f.parent, ancestorChildren.getChildAt(0));
        List<String> calls = new ArrayList<>();
        final VisualizerEvent[] observed = new VisualizerEvent[1];
        f.parent.addNodeModel(new NodeModel() {
            @Override public void added(VisualizerEvent.Added e) { observed[0] = e; calls.add("child"); }
            @Override public void removed(VisualizerEvent.Removed e) { fail(); }
            @Override public void reordered(VisualizerEvent.Reordered e) { fail(); }
            @Override public void update(VisualizerNode v) { fail(); }
            @Override public void structuralChange(VisualizerNode v) { fail(); }
        });
        ancestor.addNodeModel(new NodeModel() {
            @Override public void added(VisualizerEvent.Added e) {
                assertSame(observed[0], e);
                assertEquals(5, f.children.getChildCount());
                calls.add("ancestor");
            }
            @Override public void removed(VisualizerEvent.Removed e) { fail(); }
            @Override public void reordered(VisualizerEvent.Reordered e) { fail(); }
            @Override public void update(VisualizerNode v) { fail(); }
            @Override public void structuralChange(VisualizerNode v) { fail(); }
        });
        f.add(new int[]{0, 4}, new Snapshot(5));
        assertEquals(Arrays.asList("child", "ancestor"), calls);
    }

    /** Counts suffix movement and slot work in the actual owner's list. */
    private static final class WorkList extends ArrayList<VisualizerNode> {
        long work;
        WorkList(List<VisualizerNode> values) { super(values); }
        @Override public boolean add(VisualizerNode v) { work++; return super.add(v); }
        @Override public void add(int i, VisualizerNode v) { work += size() - i + 1; super.add(i, v); }
        @Override public VisualizerNode remove(int i) { work += size() - i; return super.remove(i); }
        @Override public VisualizerNode get(int i) { work++; return super.get(i); }
        @Override public VisualizerNode set(int i, VisualizerNode v) { work++; return super.set(i, v); }
        @Override protected void removeRange(int a, int b) { work += size() - a; super.removeRange(a, b); }
    }

    private WorkList instrument(Fixture f) throws Exception {
        WorkList list = new WorkList(f.children.getVisNodes(false));
        Field field = VisualizerChildren.class.getDeclaredField("visNodes");
        field.setAccessible(true);
        field.set(f.children, list);
        assertSame("instrument exact list", list, f.children.getVisNodes(false));
        return list;
    }

    public void testAdditionHasBoundedWork() throws Exception {
        int n = 4096, k = 2048;
        Fixture f = new Fixture(n, false);
        WorkList list = instrument(f);
        int[] indices = new int[k];
        for (int i = 0; i < k; i++) { indices[i] = i; }
        f.add(indices, new Snapshot(n + k));
        assertSame(list, f.children.getVisNodes(false));
        assertTrue("addition slot work=" + list.work, list.work <= 8L * (n + k));
    }

    public void testRemovalHasBoundedWork() throws Exception {
        int n = 4096, k = 2048;
        Fixture f = new Fixture(n, false);
        WorkList list = instrument(f);
        int[] indices = new int[k];
        for (int i = 0; i < k; i++) { indices[i] = i; }
        f.remove(indices, new Snapshot(n - k));
        assertSame(list, f.children.getVisNodes(false));
        assertTrue("removal slot work=" + list.work, list.work <= 8L * (n + k));
    }

    public void testObsoleteOwnerAndEmptyEvents() throws Exception {
        Fixture f = new Fixture(3, true);
        List<VisualizerNode> before = new ArrayList<>(f.children.getVisNodes(false));
        final List<String> calls = new ArrayList<>();
        f.parent.addNodeModel(new NodeModel() {
            @Override public void added(VisualizerEvent.Added e) { calls.add("add"); }
            @Override public void removed(VisualizerEvent.Removed e) { calls.add("remove"); }
            @Override public void reordered(VisualizerEvent.Reordered e) { fail(); }
            @Override public void update(VisualizerNode v) { fail(); }
            @Override public void structuralChange(VisualizerNode v) { fail(); }
        });
        f.add(new int[0], new Snapshot(3));
        f.remove(new int[0], new Snapshot(3));
        assertEquals(Arrays.asList("add"), calls);
        VisualizerChildren replacement = new VisualizerChildren(f.parent, new Snapshot(1));
        f.parent.notifyVisualizerChildrenChange(true, replacement);
        f.add(new int[]{0, 1}, new Snapshot(5));
        f.remove(new int[]{0, 1}, new Snapshot(1));
        identities(before, f.children.getVisNodes(false));
        assertEquals(Arrays.asList("add"), calls);
    }

    public void testRepeatedRemoveAddListenerSeesFinalStateInOrder() throws Exception {
        final Fixture f = new Fixture(6, true);
        final List<String> calls = new ArrayList<>();
        NodeModel first = observer(f, calls, "first");
        NodeModel second = observer(f, calls, "second");
        f.parent.addNodeModel(first);
        f.parent.addNodeModel(second);
        for (int i = 0; i < 3; i++) {
            f.remove(new int[]{0, 2, 4}, new Snapshot(3));
            f.add(new int[]{0, 2, 4}, new Snapshot(6));
        }
        f.remove(new int[]{0, 1, 2, 3, 4, 5}, new Snapshot(0));
        assertSame("empty owner retained", f.children, f.parent.getChildren());
        assertEquals(14, calls.size());
        for (int i = 0; i < calls.size(); i += 2) {
            assertTrue(calls.get(i).startsWith("second"));
            assertTrue(calls.get(i + 1).startsWith("first"));
        }
    }

    private NodeModel observer(final Fixture f, final List<String> calls, final String name) {
        return new NodeModel() {
            @Override public void added(VisualizerEvent.Added e) {
                assertEquals(e.getSnapshot().size(), f.children.getChildCount());
                calls.add(name + "add");
            }
            @Override public void removed(VisualizerEvent.Removed e) {
                assertEquals(e.getSnapshot().size(), f.children.getChildCount());
                calls.add(name + "remove");
            }
            @Override public void reordered(VisualizerEvent.Reordered e) { fail(); }
            @Override public void update(VisualizerNode v) { fail(); }
            @Override public void structuralChange(VisualizerNode v) { fail(); }
        };
    }

    public void testMalformedArraysRetainLegacyPartialOutcome() throws Exception {
        int[][] arrays = {{-1, -1}, {1, 1}, {2, 0}, {0, 9}, {Integer.MAX_VALUE, 0}, {0}, {}, {-1}, {9}};
        for (int size = 0; size < 5; size++) {
            for (int[] indices : arrays) {
                for (boolean add : new boolean[]{true, false}) {
                    Fixture f = new Fixture(size, true);
                    List<VisualizerNode> expected = new ArrayList<>(f.children.getVisNodes(false));
                    List<VisualizerNode> payload = new ArrayList<>();
                    Class<?> expectedFailure = null;
                    try { legacy(expected, indices, add, payload); }
                    catch (Throwable t) { expectedFailure = t.getClass(); }
                    Snapshot next = new Snapshot(expected.size());
                    VisualizerEvent.Removed removed = new VisualizerEvent.Removed(f.children, indices, f.event(false, next));
                    Class<?> failure = null;
                    try {
                        if (add) { f.add(indices, next); }
                        else { f.children.removed(removed); }
                    } catch (Throwable t) { failure = t.getClass(); }
                    assertEquals("failure " + Arrays.toString(indices), expectedFailure, failure);
                    identities(expected, f.children.getVisNodes(false));
                    if (!add) { identities(payload, removed.removed); }
                }
            }
        }
    }

    private void legacy(List<VisualizerNode> values, int[] indices, boolean add, List<VisualizerNode> payload) {
        if (add) {
            ListIterator<VisualizerNode> it = values.listIterator();
            int current = 0;
            for (int index : indices) {
                while (current++ < index) { it.next(); }
                it.add(null);
            }
        } else {
            if (indices.length == 0 || values.isEmpty()) { return; }
            assert values.size() > indices[indices.length - 1];
            int prev = Integer.MAX_VALUE;
            for (int i = indices.length - 1; i >= 0; i--) {
                if (values.isEmpty()) { continue; }
                assert indices[i] < prev;
                VisualizerNode v = values.remove(indices[i]);
                payload.add(v == null ? VisualizerNode.EMPTY : v);
            }
        }
    }
}
