package com.dylibso.chicory.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class UnsignedComparisonTest {
    @Test
    void greaterOrEqualAcrossSignedBoundary() {
        int[] ordered = {0, 1, 8, 16, Integer.MAX_VALUE, Integer.MIN_VALUE, -16, -8, -1};
        for (int a = 0; a < ordered.length; a++) {
            for (int b = 0; b < ordered.length; b++) {
                assertEquals(a >= b ? 1 : 0, OpcodeImpl.I32_GE_U(ordered[a], ordered[b]));
            }
        }
    }
}
