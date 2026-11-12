package net.unit8.notation199x;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A code point a conversion says does not turn on context is answered with its mapping alone,
 * after a cased letter, before one and between two; a code point it says does is answered otherwise
 * in one of those. Asked of {@link CaseConversion#lowercase} and {@link CaseConversion#uppercase} as
 * they answer, which write the mapping as it is and normalize nothing.
 */
class ACaseMappingIsWhatTheConversionAnswersOutsideAContextTest {

    @Test
    void lowercasingAnswersEachCodePointWithItsMappingOrTurnsOnContext() {
        int turning = heldTo(CaseConversion::lowercaseMapping, CaseConversion::lowercaseTurnsOnContext,
                CaseConversion::lowercaseLeavesAlone, CaseConversion::lowercase, "lowercase");
        assertTrue(turning > 0, "some code point's lowercase turns on context");
        assertTrue(CaseConversion.lowercaseTurnsOnContext(0x03A3));
        assertEquals("σ", text(CaseConversion.lowercaseMapping(0x03A3)));
        assertEquals("aς", CaseConversion.lowercase("AΣ"));
    }

    @Test
    void uppercasingAnswersEachCodePointWithItsMapping() {
        int turning = heldTo(CaseConversion::uppercaseMapping, CaseConversion::uppercaseTurnsOnContext,
                CaseConversion::uppercaseLeavesAlone, CaseConversion::uppercase, "uppercase");
        assertEquals(0, turning, "no code point's uppercase turns on context");
        assertEquals("SS", text(CaseConversion.uppercaseMapping('ß')));
    }

    @Test
    void aMappingIsACopyTheCallerMayChange() {
        int[] mapping = CaseConversion.uppercaseMapping('ß');
        mapping[0] = 'x';
        assertEquals("SS", text(CaseConversion.uppercaseMapping('ß')));
    }

    /** Holds each code point to what {@code conversion} answers; answers how many turn on context. */
    private static int heldTo(IntFunction<int[]> mapping, IntPredicate turnsOnContext, IntPredicate leavesAlone,
                              UnaryOperator<String> conversion, String what) {
        int turning = 0;
        for (int cp = 0; cp <= 0x10FFFF; cp++) {
            if (cp >= 0xD800 && cp <= 0xDFFF) {
                continue;
            }
            String one = Character.toString(cp);
            String mapped = text(mapping.apply(cp));
            assertFalse(mapped.isEmpty(), () -> what + " maps to at least one code point");
            boolean alike = true;
            for (List<String> around : AROUND) {
                String before = around.get(0);
                String after = around.get(1);
                alike &= conversion.apply(before + one + after)
                        .equals(conversion.apply(before) + mapped + conversion.apply(after));
            }
            int asked = cp;
            assertEquals(!turnsOnContext.test(cp), alike, () -> what + " U+" + Integer.toHexString(asked));
            assertEquals(!turnsOnContext.test(cp) && mapped.equals(one), leavesAlone.test(cp),
                    () -> what + " leaves alone U+" + Integer.toHexString(asked));
            if (turnsOnContext.test(cp)) {
                turning++;
                assertEquals(mapped, conversion.apply(one), what + " alone");
                assertNotEquals(conversion.apply("A") + mapped, conversion.apply("A" + one), what + " after A");
            }
        }
        return turning;
    }

    private static String text(int[] codePoints) {
        return new String(codePoints, 0, codePoints.length);
    }

    /** What a code point is asked beside: nothing, a cased letter before, one after, one on each side. */
    private static final List<List<String>> AROUND =
            List.of(List.of("", ""), List.of("A", ""), List.of("", "A"), List.of("A", "A"));
}
