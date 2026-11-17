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
// Modified 2026-11-15 by Hitesh Soliwal and Contributors to the Synexia Project:
// Cover viewport panning, sorting, selection, editing and clipped repaint behavior.
package org.openide.explorer.view;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.ComponentOrientation;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Properties;
import javax.swing.DefaultCellEditor;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.TableCellEditor;
import org.netbeans.junit.NbTestCase;
import org.netbeans.swing.etable.ETableColumn;
import org.netbeans.swing.etable.ETableColumnModel;
import org.netbeans.swing.etable.QuickFilter;
import org.netbeans.swing.outline.Outline;
import org.netbeans.swing.outline.OutlineModel;
import org.openide.explorer.ExplorerManager;
import org.openide.explorer.view.OutlineView.OutlineViewOutline.OutlineViewOutlineColumn;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.nodes.Sheet;
import org.openide.util.Exceptions;

/**
 *
 * @author  Jiri Rechtacek
 */
public final class OutlineViewTest extends NbTestCase {

    private OutlineViewComponent component;
    private OutlineView view;
    private Node toExpand200_299,  toExpand0_99;

    public OutlineViewTest (String testName) {
        super (testName);
    }

    @Override
    protected boolean runInEQ () {
        return true;
    }

    @Override
    public void setUp () {
        TestNode[] childrenNodes = new TestNode[3];
        for (int i = 0; i < childrenNodes.length; i ++) {
            TestNode[] leafNodes = new TestNode[100];
            for (int j = 0; j < leafNodes.length; j ++) {
                leafNodes[j] = new TestNode ("[" + (100 * i + j) + "]");
            }
            Children.Array leafs = new Children.Array ();
            leafs.add (leafNodes);
            //childrenNodes [i] = new TestNode (leafs, "[" + (i * 100) + "-" + ((i + 1) *100 - 1) + "]");
            switch (i) {
                case 0:
                    childrenNodes[i] = new TestNode (leafs, "[1-index from 0 to 99]");
                    break;
                case 1:
                    childrenNodes[i] = new TestNode (leafs, "[10-index from 100 to 199]");
                    break;
                case 2:
                    childrenNodes[i] = new TestNode (leafs, "[2-index from 200 to 299]");
                    break;
                default:
                    fail ("Unexcepted value " + i);
            }
            if (toExpand0_99 == null) {
                toExpand0_99 = childrenNodes[i];
            }
            toExpand200_299 = childrenNodes[i];
        }

        Children.Array children = new Children.Array ();
        children.add (childrenNodes);

        Node rootNode = new TestNode (children, "[0 - 1000]");

        component = new OutlineViewComponent (rootNode);
        view = component.getOutlineView ();
    }

    public void testNaturallySortingTree () throws InterruptedException, IllegalAccessException, InvocationTargetException {
        view.expandNode (toExpand0_99);

        // should look like
        // - [1-index from 0 to 99]
        //   [0]
        //   [1]
        //   [2]
        //   ....
        // + [10-index from 100 to 199]
        // + [2-index from 200 to 299]
        assertEquals ("[1-index from 0 to 99]", view.getOutline ().getValueAt (0, 0).toString ());
        assertEquals ("[0]", view.getOutline ().getValueAt (1, 0).toString ());
        assertEquals ("[1]", view.getOutline ().getValueAt (2, 0).toString ());
        assertEquals ("[2]", view.getOutline ().getValueAt (3, 0).toString ());
        assertEquals ("[10-index from 100 to 199]", view.getOutline ().getValueAt (101, 0).toString ());
        assertEquals ("[2-index from 200 to 299]", view.getOutline ().getValueAt (102, 0).toString ());
    }

    public void testDescendingSortingTreeWithNaturallyStringOrdering () throws InterruptedException, IllegalAccessException, InvocationTargetException {
        ETableColumnModel etcm = (ETableColumnModel) view.getOutline ().getColumnModel ();
        ETableColumn etc = (ETableColumn) etcm.getColumn (0); // tree column
        etcm.setColumnSorted (etc, false, 1); // descending order
        etc.setNestedComparator (testComarator);
        view.expandNode (toExpand200_299);

//        org.openide.DialogDescriptor dd = new org.openide.DialogDescriptor (component, "", true, null);
//        java.awt.Dialog d = org.openide.DialogDisplayer.getDefault ().createDialog (dd);
//        d.setVisible (true);

        // should look like
        // - [2-index from 200 to 299]
        //   [299]
        //   [298]
        // + [10-index from 100 to 199]
        //   ....
        // + [1-index from 0 to 99]
        assertEquals ("[2-index from 200 to 299]", view.getOutline ().getValueAt (1, 0).toString ());
        assertEquals ("[10-index from 100 to 199]", view.getOutline ().getValueAt (0, 0).toString ());
        assertEquals ("[299]", view.getOutline ().getValueAt (2, 0).toString ());
        assertEquals ("[298]", view.getOutline ().getValueAt (3, 0).toString ());
    }

    public void testDescendingSortingTreeWithNaturallyStringOrderingViaETable () throws InterruptedException, IllegalAccessException, InvocationTargetException {
        ETableColumnModel etcm = (ETableColumnModel) view.getOutline ().getColumnModel ();
        ETableColumn etc = (ETableColumn) etcm.getColumn (0); // tree column
        etc.setNestedComparator (testComarator);
        view.getOutline ().setColumnSorted (0, false, 1); // descending order
        view.expandNode (toExpand200_299);

        assertEquals ("[2-index from 200 to 299]", view.getOutline ().getValueAt (1, 0).toString ());
        assertEquals ("[10-index from 100 to 199]", view.getOutline ().getValueAt (0, 0).toString ());
        assertEquals ("[299]", view.getOutline ().getValueAt (2, 0).toString ());
        assertEquals ("[298]", view.getOutline ().getValueAt (3, 0).toString ());
    }

    public void testAscendingSortingTreeWithNaturallyStringOrdering () throws InterruptedException, IllegalAccessException, InvocationTargetException {
        ETableColumnModel etcm = (ETableColumnModel) view.getOutline ().getColumnModel ();
        ETableColumn etc = (ETableColumn) etcm.getColumn (0); // tree column
        etcm.setColumnSorted (etc, true, 1); // ascending order
        view.expandNode (toExpand0_99);

//        org.openide.DialogDescriptor dd = new org.openide.DialogDescriptor (component, "", true, null);
//        java.awt.Dialog d = org.openide.DialogDisplayer.getDefault ().createDialog (dd);
//        d.setVisible (true);

        // should look like
        // - [1-index from 0 to 99]
        //   [0]
        //   [10]
        //   [11]
        //   ....
        // + [10-index from 100 to 199]
        // + [2-index from 200 to 299]
        assertEquals ("[1-index from 0 to 99]", view.getOutline ().getValueAt (0, 0).toString ());
        assertEquals ("[0]", view.getOutline ().getValueAt (1, 0).toString ());
        assertEquals ("[10]", view.getOutline ().getValueAt (2, 0).toString ());
        assertEquals ("[11]", view.getOutline ().getValueAt (3, 0).toString ());
        assertEquals ("[10-index from 100 to 199]", view.getOutline ().getValueAt (101, 0).toString ());
        assertEquals ("[2-index from 200 to 299]", view.getOutline ().getValueAt (102, 0).toString ());
    }

    public void testAscendingSortingTreeWithNaturallyStringOrderingViaETable () throws InterruptedException, IllegalAccessException, InvocationTargetException {
        ETableColumnModel etcm = (ETableColumnModel) view.getOutline ().getColumnModel ();
        view.getOutline ().setColumnSorted (0, true, 1); // ascending order
        view.expandNode (toExpand0_99);

        assertEquals ("[1-index from 0 to 99]", view.getOutline ().getValueAt (0, 0).toString ());
        assertEquals ("[0]", view.getOutline ().getValueAt (1, 0).toString ());
        assertEquals ("[10]", view.getOutline ().getValueAt (2, 0).toString ());
        assertEquals ("[11]", view.getOutline ().getValueAt (3, 0).toString ());
        assertEquals ("[10-index from 100 to 199]", view.getOutline ().getValueAt (101, 0).toString ());
        assertEquals ("[2-index from 200 to 299]", view.getOutline ().getValueAt (102, 0).toString ());
    }

    public void testDescendingSortingTreeWithCustomComparator () throws InterruptedException, IllegalAccessException, InvocationTargetException {
        ETableColumnModel etcm = (ETableColumnModel) view.getOutline ().getColumnModel ();
        ETableColumn etc = (ETableColumn) etcm.getColumn (0); // tree column
        etc.setNestedComparator (testComarator);
        etcm.setColumnSorted (etc, false, 1); // descending order
        view.expandNode (toExpand200_299);

//        org.openide.DialogDescriptor dd = new org.openide.DialogDescriptor (component, "", true, null);
//        java.awt.Dialog d = org.openide.DialogDisplayer.getDefault ().createDialog (dd);
//        d.setVisible (true);

        // should look like
        // + [10-index from 100 to 199]
        // - [2-index from 200 to 299]
        //   [299]
        //   [298]
        //   ....
        // + [1-index from 0 to 99]
        assertEquals ("[10-index from 100 to 199]", view.getOutline ().getValueAt (0, 0).toString ());
        assertEquals ("[2-index from 200 to 299]", view.getOutline ().getValueAt (1, 0).toString ());
        assertEquals ("[299]", view.getOutline ().getValueAt (2, 0).toString ());
        assertEquals ("[298]", view.getOutline ().getValueAt (3, 0).toString ());
    }

    public void testDescendingSortingTreeWithCustomComparatorViaETable() throws InterruptedException, IllegalAccessException, InvocationTargetException {
        ETableColumnModel etcm = (ETableColumnModel) view.getOutline ().getColumnModel ();
        ETableColumn etc = (ETableColumn) etcm.getColumn (0); // tree column
        etc.setNestedComparator (testComarator);
        view.getOutline().setColumnSorted (0, false, 1); // descending order
        view.expandNode (toExpand200_299);

        assertEquals ("[10-index from 100 to 199]", view.getOutline ().getValueAt (0, 0).toString ());
        assertEquals ("[2-index from 200 to 299]", view.getOutline ().getValueAt (1, 0).toString ());
        assertEquals ("[299]", view.getOutline ().getValueAt (2, 0).toString ());
        assertEquals ("[298]", view.getOutline ().getValueAt (3, 0).toString ());
    }

    public void testAscendingSortingTreeWithCustomComparator () throws InterruptedException, IllegalAccessException, InvocationTargetException {
        ETableColumnModel etcm = (ETableColumnModel) view.getOutline ().getColumnModel ();
        ETableColumn etc = (ETableColumn) etcm.getColumn (0); // tree column
        etc.setNestedComparator (testComarator);
        etcm.setColumnSorted (etc, true, 1); // ascending order

//        org.openide.DialogDescriptor dd = new org.openide.DialogDescriptor (component, "", true, null);
//        java.awt.Dialog d = org.openide.DialogDisplayer.getDefault ().createDialog (dd);
//        d.setVisible (true);

        view.expandNode (toExpand0_99);

        // should look like
        // - [1-index from 0 to 99]
        //   [0]
        //   [1]
        //   [2]
        //   ....
        // + [2-index from 200 to 299]
        // + [10-index from 100 to 199]
        assertEquals ("[1-index from 0 to 99]", view.getOutline ().getValueAt (0, 0).toString ());
        assertEquals ("[0]", view.getOutline ().getValueAt (1, 0).toString ());
        assertEquals ("[1]", view.getOutline ().getValueAt (2, 0).toString ());
        assertEquals ("[2]", view.getOutline ().getValueAt (3, 0).toString ());
        assertEquals ("[2-index from 200 to 299]", view.getOutline ().getValueAt (101, 0).toString ());
    }
    
    public void testAscendingSortingTreeWithCustomComparatorViaETable() throws InterruptedException, IllegalAccessException, InvocationTargetException {
        ETableColumnModel etcm = (ETableColumnModel) view.getOutline ().getColumnModel ();
        ETableColumn etc = (ETableColumn) etcm.getColumn (0); // tree column
        etc.setNestedComparator (testComarator);
        view.getOutline().setColumnSorted (0, true, 1); // ascending order
        view.expandNode (toExpand0_99);

        assertEquals ("[1-index from 0 to 99]", view.getOutline ().getValueAt (0, 0).toString ());
        assertEquals ("[0]", view.getOutline ().getValueAt (1, 0).toString ());
        assertEquals ("[1]", view.getOutline ().getValueAt (2, 0).toString ());
        assertEquals ("[2]", view.getOutline ().getValueAt (3, 0).toString ());
        assertEquals ("[2-index from 200 to 299]", view.getOutline ().getValueAt (101, 0).toString ());
    }
    
    public void testColumnSortability() throws Exception {
        ETableColumnModel etcm = (ETableColumnModel) view.getOutline().getColumnModel();
        ETableColumn etc = (ETableColumn) etcm.getColumn(1);
        boolean sortable = etc.isSortingAllowed();
        assertEquals("Has to be sortable, initially.", true, sortable);
        view.setPropertyColumnAttribute("unitTestPropName", "SortableColumn", Boolean.FALSE);
        sortable = etc.isSortingAllowed();
        assertEquals("Should not be sortable after attribute change.", false, sortable);
        view.setPropertyColumnAttribute("unitTestPropName", "SortableColumn", Boolean.TRUE);
        sortable = etc.isSortingAllowed();
        assertEquals("Sortable, again.", true, sortable);
    }
    
    public void testPropertiesPersistence() throws Exception {
        OutlineView ov = new OutlineView ("test-outline-view-component");
        Outline outline = ov.getOutline();
        ov.addPropertyColumn("c1", "Column 1", "Description 1");
        ov.addPropertyColumn("c2", "Column 2", "Description 2");
        Properties p = new Properties();
        outline.writeSettings(p, "test");
        
        OutlineView ov2 = new OutlineView ("test-outline-view-component");
        Outline outline2 = ov2.getOutline();
        outline2.readSettings(p, "test");
        
        int cc = outline.getColumnCount();
        int cc2 = outline2.getColumnCount();
        assertEquals("Column count", cc, cc2);
        for (int c = 0; c < cc; c++) {
            String cn = outline.getColumnName(c);
            String cn2 = outline2.getColumnName(c);
            assertEquals("Column "+c+" name", cn, cn2);
            OutlineViewOutlineColumn oc = (OutlineViewOutlineColumn) outline.getColumnModel().getColumn(c);
            OutlineViewOutlineColumn oc2 = (OutlineViewOutlineColumn) outline2.getColumnModel().getColumn(c);
            String shortDescription = oc.getShortDescription(null);
            String shortDescription2 = oc2.getShortDescription(null);
            assertEquals("Column "+c+" short description", shortDescription, shortDescription2);
        }
        
        ETableColumnModel etcm = (ETableColumnModel) outline2.getColumnModel();
        etcm.setColumnHidden(etcm.getColumn(1), true);
        outline2.writeSettings(p, "test");
        
        ov2 = new OutlineView ("test-outline-view-component");
        outline2 = ov2.getOutline();
        outline2.readSettings(p, "test");
        cc2 = outline2.getColumnCount();
        assertEquals("Column count", cc - 1, cc2);
        OutlineViewOutlineColumn oc = (OutlineViewOutlineColumn) outline.getColumnModel().getColumn(2);
        OutlineViewOutlineColumn oc2 = (OutlineViewOutlineColumn) outline2.getColumnModel().getColumn(1);
        String shortDescription = oc.getShortDescription(null);
        String shortDescription2 = oc2.getShortDescription(null);
        assertEquals("Last column short description", shortDescription, shortDescription2);
    }

    /**
     * Test for bug 236331 - After reading the persistence settings all the
     * OutlineView property cells become empty.
     *
     * @throws Exception
     */
    public void testPropertiesPersistence2() throws Exception {

        TestNode[] childrenNodes = new TestNode[2];
        childrenNodes[0] = new TestNode(Children.LEAF, "First");
        childrenNodes[1] = new TestNode(Children.LEAF, "Second");
        Children.Array children = new Children.Array();
        children.add(childrenNodes);
        Node rootNode = new TestNode(children, "Invisible Root");

        OutlineViewComponentWithLabels comp
                = new OutlineViewComponentWithLabels(rootNode);
        OutlineView ov = comp.getOutlineView();
        Outline o = ov.getOutline();

        ov.expandNode(rootNode);
        o.moveColumn(1, 0);
        assertEquals("First", getDummyValue(o.getValueAt(0, 0)));
        assertEquals("Second", getDummyValue(o.getValueAt(1, 0)));

        Properties p = new Properties();
        o.writeSettings(p, "test");

        OutlineViewComponentWithLabels comp2
                = new OutlineViewComponentWithLabels(rootNode);
        OutlineView ov2 = comp2.getOutlineView();
        Outline o2 = ov2.getOutline();

        ov2.readSettings(p, "test");
        ov2.expandNode(rootNode);
        assertNotSame(o, o2);

        // ensure the order of columns was restored
        assertEquals("First", getDummyValue(o2.getValueAt(0, 0)));
        assertEquals("Second", getDummyValue(o2.getValueAt(1, 0)));
    }

    public void testHorizontalTreeScrollKeepsSortAndFilterState() {
        view.expandNode(toExpand0_99);
        OutlineView.OutlineViewOutline outline = (OutlineView.OutlineViewOutline) view.getOutline();
        int[] calls = new int[2];
        ETableColumn tree = (ETableColumn) outline.getColumnModel().getColumn(0);
        tree.setNestedComparator((Comparator<Node>) (left, right) -> {
            calls[0]++;
            return left.getDisplayName().compareTo(right.getDisplayName());
        });
        outline.setColumnSorted(0, true, 1);
        outline.setQuickFilter(0, (QuickFilter) value -> {
            calls[1]++;
            return !value.toString().equals("[3]");
        });
        int[] before = rowMapping(outline);
        assertTrue("Fixture exercises sorting", calls[0] > 0);
        assertTrue("Fixture exercises filtering", calls[1] > 0);
        Arrays.fill(calls, 0);

        outline.setTreePositionX(23);

        assertTrue("Panning preserves filtered row order", Arrays.equals(before, rowMapping(outline)));
        assertEquals("Panning does not sort unchanged rows", 0, calls[0]);
        assertEquals("Panning does not filter unchanged rows", 0, calls[1]);
        assertEquals(23, outline.getTreePositionX());
    }

    public void testHorizontalTreeScrollKeepsSelectionAndEditor() {
        view.expandNode(toExpand0_99);
        OutlineView.OutlineViewOutline outline = (OutlineView.OutlineViewOutline) view.getOutline();
        outline.setColumnSorted(0, true, 1);
        outline.setRowSelectionInterval(1, 1);
        int propertyColumn = outline.convertColumnIndexToView(1);
        outline.setColumnSelectionInterval(propertyColumn, propertyColumn);
        outline.getColumnModel().getColumn(propertyColumn).setCellEditor(new DefaultCellEditor(new JTextField()));
        assertTrue("Fixture starts a real property editor", outline.editCellAt(1, propertyColumn,
                new ActionEvent(outline, ActionEvent.ACTION_PERFORMED, "edit")));
        int[] selected = outline.getSelectedRows();
        Object selectedValue = outline.getValueAt(1, 0);
        TableCellEditor editor = outline.getCellEditor();
        Component editorComponent = outline.getEditorComponent();
        int editingRow = outline.getEditingRow();
        int editingColumn = outline.getEditingColumn();
        try {
            outline.setTreePositionX(17);
            assertTrue("Panning preserves selection", Arrays.equals(selected, outline.getSelectedRows()));
            assertSame(selectedValue, outline.getValueAt(1, 0));
            assertTrue(outline.isEditing());
            assertSame(editor, outline.getCellEditor());
            assertSame(editorComponent, outline.getEditorComponent());
            assertEquals(editingRow, outline.getEditingRow());
            assertEquals(editingColumn, outline.getEditingColumn());
        } finally {
            if (outline.isEditing()) {
                outline.getCellEditor().cancelCellEditing();
            }
        }
    }

    public void testHorizontalTreeScrollRepaintsOnlyVisibleTreeColumn() {
        for (boolean leftToRight : new boolean[] { true, false }) {
            for (boolean moved : new boolean[] { false, true }) {
                RepaintOutline outline = repaintOutline();
                outline.setComponentOrientation(leftToRight
                        ? ComponentOrientation.LEFT_TO_RIGHT : ComponentOrientation.RIGHT_TO_LEFT);
                if (moved) {
                    outline.moveColumn(0, 1);
                }
                int treeX = leftToRight == moved ? 180 : 0;
                outline.record(new Rectangle(treeX + 20, 200, 160, 40));

                outline.setTreePositionX(13);

                assertEquals("Only the visible tree-column intersection is dirty",
                        new Rectangle(treeX + 20, 200, 100, 40), outline.dirty);
                assertEquals(1, outline.repaints);
                assertEquals("Column geometry does not need model rows", 0, outline.rowCountCalls);
            }
        }
    }

    public void testHorizontalTreeScrollSkipsUnchangedHiddenAndOffscreenColumns() {
        RepaintOutline outline = repaintOutline();
        outline.record(new Rectangle(0, 100, 100, 40));
        outline.setTreePositionX(0);
        assertEquals("Unchanged offset is a no-op", 0, outline.repaints);

        outline.record(new Rectangle(150, 100, 100, 40));
        outline.setTreePositionX(5);
        assertEquals("Offscreen tree column is not dirty", 0, outline.repaints);

        outline.record(new Rectangle());
        outline.setTreePositionX(6);
        assertEquals("Empty viewport is not dirty", 0, outline.repaints);

        ETableColumnModel columns = (ETableColumnModel) outline.getColumnModel();
        columns.setColumnHidden(columns.getColumn(outline.convertColumnIndexToView(0)), true);
        outline.record(new Rectangle(0, 100, 300, 40));
        outline.setTreePositionX(7);
        assertEquals("Hidden tree column is not dirty", 0, outline.repaints);
        assertEquals("The position is retained for a later visible column", 7, outline.getTreePositionX());
    }

    private static int[] rowMapping(Outline outline) {
        int[] rows = new int[outline.getRowCount()];
        for (int row = 0; row < rows.length; row++) {
            rows[row] = outline.convertRowIndexToModel(row);
        }
        return rows;
    }

    private RepaintOutline repaintOutline() {
        RepaintOutline outline = new RepaintOutline(view.getOutline().getOutlineModel());
        outline.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        outline.setSize(400, 6000);
        assertEquals(2, outline.getColumnCount());
        outline.getColumnModel().getColumn(0).setWidth(120);
        outline.getColumnModel().getColumn(1).setWidth(180);
        return outline;
    }

    private static final class RepaintOutline extends OutlineView.OutlineViewOutline {
        private Rectangle visible;
        private Rectangle dirty;
        private int repaints;
        private int rowCountCalls;

        RepaintOutline(OutlineModel model) {
            super(model, new PropertiesRowModel());
        }

        void record(Rectangle bounds) {
            visible = bounds;
            dirty = null;
            repaints = 0;
            rowCountCalls = 0;
        }

        @Override public Rectangle getVisibleRect() {
            return visible == null ? super.getVisibleRect() : new Rectangle(visible);
        }

        @Override public int getRowCount() {
            if (visible != null) {
                rowCountCalls++;
            }
            return super.getRowCount();
        }

        @Override public void repaint(long delay, int x, int y, int width, int height) {
            if (visible != null) {
                dirty = new Rectangle(x, y, width, height);
                repaints++;
            }
        }
    }

    private String getDummyValue(Object prop) {
        if (prop instanceof TestNode.DummyProperty) {
            TestNode.DummyProperty dummyProp = (TestNode.DummyProperty) prop;
            Object val = dummyProp.getValue("unitTestPropName");
            return val == null ? null : val.toString();
        } else {
            fail("DummyProperty expected, but was " + prop
                    + " (" + (prop == null ? "null" : prop.getClass()) + "). "
                    + "The order of columns is probably incorrect.");
            return null;
        }
    }

    public void testEmptyManagerSelectionDoesNotEnumerateRows() throws Exception {
        TestNode[] leafNodes = new TestNode[512];
        for (int i = 0; i < leafNodes.length; i++) {
            leafNodes[i] = new TestNode("[leaf-" + i + "]");
        }
        Children.Array children = new Children.Array();
        children.add(leafNodes);
        TestNode root = new TestNode(children, "[root]");
        CountingOutlineView counting = new CountingOutlineView();
        OutlineViewComponent owner = new OutlineViewComponent(root, counting);
        counting.expandNode(root);
        assertEquals(leafNodes.length, counting.getOutline().getRowCount());

        counting.getOutline().setRowSelectionInterval(1, 1);
        assertEquals(1, owner.getExplorerManager().getSelectedNodes().length);
        counting.rowReads = 0;
        owner.getExplorerManager().setSelectedNodes(new Node[0]);

        assertEquals(0, counting.getOutline().getSelectedRowCount());
        assertEquals("Clearing selection must not read any unselected rows", 0, counting.rowReads);
    }

    private static final class CountingOutlineView extends OutlineView {
        private int rowReads;

        CountingOutlineView() {
            super("test-outline-view-component");
        }

        @Override
        Node getNodeFromRow(int row) {
            rowReads++;
            return super.getNodeFromRow(row);
        }
    }

    private class OutlineViewComponent extends JPanel implements ExplorerManager.Provider {

        private final ExplorerManager manager = new ExplorerManager ();
        private OutlineView view;

        private OutlineViewComponent (Node rootNode) {
            this(rootNode, new OutlineView ("test-outline-view-component"));
        }

        private OutlineViewComponent (Node rootNode, OutlineView actualView) {
            setLayout (new BorderLayout ());
            manager.setRootContext (rootNode);

            Node.Property[] props = rootNode.getPropertySets ()[0].getProperties ();
            view = actualView;
            view.setProperties (props);

            view.getOutline ().setRootVisible (false);

            add (view, BorderLayout.CENTER);
        }

        public ExplorerManager getExplorerManager () {
            return manager;
        }

        public OutlineView getOutlineView () {
            return view;
        }
    }

    /**
     * View component with outline where column display names are different from
     * column property names. Used in {@link #testPropertiesPersistence2()}.
     */
    private class OutlineViewComponentWithLabels extends JPanel implements
            ExplorerManager.Provider {

        private final ExplorerManager manager = new ExplorerManager();
        private OutlineView view;

        private OutlineViewComponentWithLabels(Node rootNode) {
            setLayout(new BorderLayout());
            manager.setRootContext(rootNode);

            view = new OutlineView("test-outline-view-component");
            view.setPropertyColumns("unitTestPropName", "TestProperty");

            view.getOutline().setRootVisible(false);

            add(view, BorderLayout.CENTER);
        }

        @Override
        public ExplorerManager getExplorerManager() {
            return manager;
        }

        public OutlineView getOutlineView() {
            return view;
        }
    }

    static class TestNode extends AbstractNode {

        public TestNode (String name) {
            super (Children.LEAF);
            setName (name);
        }

        public TestNode (Children children, String name) {
            super (children);
            setName (name);
        }

        @Override
        protected Sheet createSheet () {
            Sheet s = super.createSheet ();
            Sheet.Set ss = s.get (Sheet.PROPERTIES);
            if (ss == null) {
                ss = Sheet.createPropertiesSet ();
                s.put (ss);
            }
            ss.put (new DummyProperty (getName ()));
            return s;
        }

        void forcePropertyChangeEvent () {
            firePropertyChange ("unitTestPropName", null, new Object ());
        }

        class DummyProperty extends Property<String> {

            public DummyProperty (String val) {
                super (String.class);
                setName ("unitTestPropName");
                try {
                    setValue (val);
                } catch (IllegalAccessException ex) {
                    Exceptions.printStackTrace (ex);
                } catch (IllegalArgumentException ex) {
                    Exceptions.printStackTrace (ex);
                } catch (InvocationTargetException ex) {
                    Exceptions.printStackTrace (ex);
                }
            }

            public boolean canRead () {
                return true;
            }

            public String getValue () throws IllegalAccessException,
                    InvocationTargetException {
                return (String) getValue ("unitTestPropName");
            }

            public boolean canWrite () {
                return true;
            }

            public void setValue (String val) throws IllegalAccessException,
                    IllegalArgumentException,
                    InvocationTargetException {
                setValue ("unitTestPropName", val);
            }
        }
    }

    private Comparator testComarator = new Comparator () {

        public int compare (Object o1, Object o2) {
            assertTrue (o1 + " instanceof String", o1 instanceof Node);
            assertTrue (o2 + " instanceof String", o2 instanceof Node);
            Node n1 = (Node) o1;
            Node n2 = (Node) o2;

            // my comparator
            return getInteger (n1.getDisplayName ()).compareTo (getInteger (n2.getDisplayName ()));
        }

        private Integer getInteger (Object o) {
            String s = o.toString ();
            assertTrue (s + "startsWith (\"[\") && s.endsWith (\"]\")", s.startsWith ("[") && s.endsWith ("]"));
            int end = s.indexOf ("-");
            if (end != -1) {
                s = s.substring (1, end);
            } else {
                s = s.substring (1, s.length () - 1);
            }
            //System.out.println ("###: " + o.toString () + " => " + Integer.parseInt (s));
            return Integer.parseInt (s);
        }
    };
}
