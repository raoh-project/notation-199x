package net.unit8.notation199x;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.IntPredicate;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A code point a case conversion is said to leave alone is one it answers as it is alone, after a
 * cased letter, before one, and between two; and every other code point is one it answers
 * otherwise in one of those. Asked of the conversion as it answers, and not of its tables.
 *
 * <p>And a stable starter is a starter that normalization leaves as it is.
 */
class WhatACaseConversionLeavesAloneIsWhatItAnswersAsItIsTest {

    @Test
    void lowercasingLeavesAloneWhatItAnswersAsItIs() {
        heldTo(CaseConversion::lowercaseLeavesAlone, CaseConversion::lowercase, "lowercase");
    }

    @Test
    void uppercasingLeavesAloneWhatItAnswersAsItIs() {
        heldTo(CaseConversion::uppercaseLeavesAlone, CaseConversion::uppercase, "uppercase");
    }

    @Test
    void aStableStarterIsAStarterNormalizationLeavesAsItIs() {
        for (int cp = 0; cp <= 0x10FFFF; cp++) {
            if (cp >= 0xD800 && cp <= 0xDFFF || !Normalization.isStableStarter(Normalization.Form.NFC, cp)) {
                continue;
            }
            String one = Character.toString(cp);
            assertEquals(0, Normalization.combiningClass(cp), one);
            assertEquals(one, Normalization.nfc(one), one);
        }
    }

    private static void heldTo(IntPredicate leavesAlone, UnaryOperator<String> conversion, String what) {
        for (int cp = 0; cp <= 0x10FFFF; cp++) {
            if (cp >= 0xD800 && cp <= 0xDFFF) {
                continue;
            }
            String one = Character.toString(cp);
            boolean asItIs = true;
            for (List<String> around : AROUND) {
                String before = around.get(0);
                String after = around.get(1);
                asItIs &= conversion.apply(before + one + after)
                        .equals(conversion.apply(before) + one + conversion.apply(after));
            }
            int asked = cp;
            assertEquals(leavesAlone.test(cp), asItIs, () -> what + " U+" + Integer.toHexString(asked));
        }
        assertTrue(leavesAlone.test('1'), what + " leaves a digit alone");
    }

    /** What a code point is asked beside: nothing, a cased letter before, one after, one on each side. */
    private static final List<List<String>> AROUND =
            List.of(List.of("", ""), List.of("A", ""), List.of("", "A"), List.of("A", "A"));
}
