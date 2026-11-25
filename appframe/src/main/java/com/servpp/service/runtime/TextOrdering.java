package com.servpp.service.runtime;

import java.nio.charset.Charset;

/** Compares code points after padding the shorter string with U+0020. */
public final class TextOrdering {
    private TextOrdering() { }

    public enum Collation { BINARY, EBCDIC }

    public static int compareText(String left, String right, Collation collation) {
        if (left == null || right == null) {
            throw new IllegalArgumentException("String ordering requires non-null operands.");
        }
        int leftOffset = 0;
        int rightOffset = 0;
        while (leftOffset < left.length() || rightOffset < right.length()) {
            int a = ' ';
            int b = ' ';
            if (leftOffset < left.length()) {
                a = left.codePointAt(leftOffset);
                leftOffset += Character.charCount(a);
            }
            if (rightOffset < right.length()) {
                b = right.codePointAt(rightOffset);
                rightOffset += Character.charCount(b);
            }
            int comparison = collation == Collation.EBCDIC
                    ? Integer.compare(Ibm037.rank(a), Ibm037.rank(b))
                    : Integer.compare(a, b);
            if (comparison != 0) return comparison;
        }
        return 0;
    }

    private static final class Ibm037 {
        private static final int SIZE = 256;
        private static final int[] RANKS = ranks();

        private static int rank(int codePoint) {
            if (codePoint < RANKS.length && RANKS[codePoint] >= 0) return RANKS[codePoint];
            return SIZE + codePoint;
        }

        private static int[] ranks() {
            // IBM-037 is a single-byte code page whose characters are all in Latin-1.
            var charset = Charset.forName("IBM037");
            int[] ranks = new int[SIZE];
            java.util.Arrays.fill(ranks, -1);
            for (int value = 0; value < SIZE; value++) {
                int codePoint = new String(new byte[] {(byte) value}, charset).codePointAt(0);
                ranks[codePoint] = value;
            }
            return ranks;
        }
    }
}
