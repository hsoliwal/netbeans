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
/*
 * Copyright 2026 Hitesh Soliwal and contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.netbeans.swing.etable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Random;
import junit.framework.TestCase;

/** Differential contracts for the existing quick-search prefix owner. */
public class ETablePrefixTest extends TestCase {

    private static final Method PREFIX = prefixMethod();

    private static Method prefixMethod() {
        try {
            Method method = ETable.class.getDeclaredMethod("findMaxPrefix", String.class, String.class);
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }

    private static String prefix(String first, String second) {
        try {
            return (String) PREFIX.invoke(null, first, second);
        } catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof RuntimeException exception) {
                throw exception;
            }
            throw new AssertionError(failure.getCause());
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }

    /** Exact previous algorithm: the first failing UTF-16 prefix ends the search. */
    private static String previousPrefix(String first, String second) {
        int i = 0;
        while (first.regionMatches(true, 0, second, 0, i)) {
            i++;
        }
        i--;
        return i >= 0 ? first.substring(0, i) : null;
    }

    private static void samePrefix(String first, String second) {
        String expected = previousPrefix(first, second);
        String actual = prefix(first, second);
        if (!expected.equals(actual)) {
            assertEquals("UTF-16 prefix changed for " + first + " / " + second, expected, actual);
        }
    }

    public void testFirstStringSpellingAndSurrogateBoundaries() {
        assertEquals("MiXeD", prefix("MiXeD", "mixed!"));
        assertEquals("ab", prefix("abcdef", "abXYZ"));
        assertEquals("", prefix("", "value"));
        assertEquals("", prefix("value", ""));
        assertEquals("I", prefix("I", "\u0131"));
        assertEquals("\uD801\uDC00", prefix("\uD801\uDC00x", "\uD801\uDC28Y"));
        assertEquals("\uD801", prefix("\uD801\uDC00", "\uD801\uDC01"));
        assertEquals("\uD801", prefix("\uD801", "\uD801\uDC28"));
        assertEquals("", prefix("\uD800\uDC00", "\uD801\uDC00"));
    }

    public void testEveryUnicodeSimpleCaseMapping() {
        for (int codePoint = Character.MIN_CODE_POINT; codePoint <= Character.MAX_CODE_POINT; codePoint++) {
            int[] cases = {Character.toUpperCase(codePoint), Character.toLowerCase(codePoint), Character.toTitleCase(codePoint)};
            for (int mapped : cases) {
                if (mapped == codePoint) {
                    continue;
                }
                String first = new String(Character.toChars(codePoint));
                String second = new String(Character.toChars(mapped));
                samePrefix(first, second);
                samePrefix(second, first);
                samePrefix("a" + first + "X", "A" + second + "x");
                samePrefix(first + "a", second + "B");
                for (int end = 0; end <= first.length(); end++) {
                    samePrefix(first.substring(0, end), second);
                    samePrefix(second, first.substring(0, end));
                }
            }
        }
    }

    public void testEveryPairOfRepresentativeTwoUnitStrings() {
        char[] alphabet = {'A', 'a', 'I', 'i', '\u0130', '\u0131', 'K', '\u212A', '\u017F', 'S',
            '\u03A3', '\u03C2', '\u03C3', '\uD800', '\uD801', '\uD803', '\uDBFF', '\uDC00', '\uDC28', '\uDFFF', '\u0000'};
        String[] words = new String[alphabet.length * alphabet.length];
        int at = 0;
        for (char first : alphabet) {
            for (char second : alphabet) {
                words[at++] = new String(new char[] {first, second});
            }
        }
        for (String first : words) {
            for (String second : words) {
                samePrefix(first, second);
            }
        }
    }

    public void testRandomMixedAndUnpairedUtf16Strings() {
        Random random = new Random(0x4554505245464958L);
        char[] frequent = {'A', 'a', 'I', '\u0130', '\u0131', '\u03A3', '\u03C2', '\u03C3',
            '\uD800', '\uD801', '\uD803', '\uDBFF', '\uDC00', '\uDC28', '\uDFFF', '\u0000'};
        for (int example = 0; example < 50_000; example++) {
            char[] first = new char[random.nextInt(25)];
            for (int i = 0; i < first.length; i++) {
                first[i] = random.nextBoolean() ? frequent[random.nextInt(frequent.length)] : (char) random.nextInt(65536);
            }
            char[] second = first.clone();
            for (int i = 0; i < second.length; i++) {
                if (random.nextInt(5) == 0) {
                    second[i] = random.nextBoolean() ? Character.toLowerCase(second[i]) : frequent[random.nextInt(frequent.length)];
                }
            }
            String left = new String(first);
            String right = new String(second);
            samePrefix(left, right);
            samePrefix(right, left);
            if (second.length > 0) {
                samePrefix(left, right.substring(0, random.nextInt(second.length)));
            }
        }
    }

    public void testLongEqualAndCaseFoldedLabels() {
        String first = "a".repeat(20_000);
        String second = "A".repeat(20_000);
        assertSame(first, prefix(first, first));
        assertSame(first, prefix(first, second));
        assertEquals(first, prefix(first + "x", second + "Y"));
        String supplementary = "\uD801\uDC00".repeat(2_000);
        String lower = "\uD801\uDC28".repeat(2_000);
        assertEquals(supplementary, prefix(supplementary, lower));
    }

    public void testNullArgumentsKeepTheirExceptionType() {
        try {
            prefix(null, "value");
            fail("null first argument accepted");
        } catch (NullPointerException expected) {
            // Original private owner rejects a null string.
        }
        try {
            prefix("value", null);
            fail("null second argument accepted");
        } catch (NullPointerException expected) {
            // Original private owner rejects a null string.
        }
    }
}
