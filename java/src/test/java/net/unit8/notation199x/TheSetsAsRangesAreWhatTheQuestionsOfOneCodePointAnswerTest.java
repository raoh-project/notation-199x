package net.unit8.notation199x;

import net.unit8.notation199x.pattern.CodePoints;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * A set the library answers as ranges holds the scalar values the question of one code point it
 * stands for answers true for, and no other: {@link WhiteSpace#codePoints} is {@link WhiteSpace#contains},
 * and {@link Normalization#stableStarters} is {@link Normalization#isStableStarter} in each form.
 */
class TheSetsAsRangesAreWhatTheQuestionsOfOneCodePointAnswerTest {

    @Test
    void theWhiteSpaceRangesAreTheCodePointsThatAreWhiteSpace() {
        CodePoints set = WhiteSpace.codePoints();
        for (int cp = 0; cp <= Character.MAX_CODE_POINT; cp++) {
            if (cp >= 0xD800 && cp <= 0xDFFF) {
                assertFalse(WhiteSpace.contains(cp));
                continue;
            }
            assertEquals(WhiteSpace.contains(cp), set.has(cp), "U+" + Integer.toHexString(cp));
        }
        assertEquals(25, set.size());
    }

    @Test
    void theStableStarterRangesAreTheCodePointsThatAreStableStarters() {
        for (Normalization.Form form : Normalization.Form.values()) {
            CodePoints set = Normalization.stableStarters(form);
            long members = 0;
            for (int cp = 0; cp <= Character.MAX_CODE_POINT; cp++) {
                if (cp >= 0xD800 && cp <= 0xDFFF) {
                    continue;
                }
                boolean stable = Normalization.isStableStarter(form, cp);
                assertEquals(stable, set.has(cp), form + " U+" + Integer.toHexString(cp));
                members += stable ? 1 : 0;
            }
            assertEquals(members, set.size(), form.toString());
        }
    }
}
