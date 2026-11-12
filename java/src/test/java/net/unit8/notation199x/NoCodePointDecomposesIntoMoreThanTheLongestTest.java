package net.unit8.notation199x;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * No code point comes to more code points in a form's decomposition than
 * {@link Normalization#longestDecomposition} says, some code point comes to as many, and no code
 * point, and no text, comes to more than that many times as many once normalized.
 *
 * <p>Asked of {@link Normalization#normalize} as it answers: the decomposition of a code point in NFC
 * and NFD is its NFD, and in NFKC and NFKD its NFKD.
 */
class NoCodePointDecomposesIntoMoreThanTheLongestTest {

    @Test
    void theLongestDecompositionOfEachFormIsTheMostAnyCodePointComesTo() {
        for (Normalization.Form form : Normalization.Form.values()) {
            boolean compatibility = form == Normalization.Form.NFKC || form == Normalization.Form.NFKD;
            Normalization.Form decomposing = compatibility ? Normalization.Form.NFKD : Normalization.Form.NFD;
            int longest = Normalization.longestDecomposition(form);
            int most = 0;
            for (int cp = 0; cp <= Character.MAX_CODE_POINT; cp++) {
                // A code point with no decomposition is its own in every form: one code point.
                if (cp >= 0xD800 && cp <= 0xDFFF || Normalization.decomposeOne(cp, compatibility) == null) {
                    continue;
                }
                String one = Character.toString(cp);
                most = Math.max(most, count(Normalization.normalize(decomposing, one)));
                int normalized = count(Normalization.normalize(form, one));
                int asked = cp;
                assertTrue(normalized <= longest, () -> form + " U+" + Integer.toHexString(asked));
            }
            assertEquals(most, longest, form.toString());
        }
        assertEquals(4, Normalization.longestDecomposition(Normalization.Form.NFD));
        assertEquals(4, count(Normalization.normalize(Normalization.Form.NFD, "ᾂ")));
        assertEquals(18, count(Normalization.normalize(Normalization.Form.NFKD, "ﷺ")));
    }

    /**
     * Every text of up to three code points of an alphabet that holds the longest decomposition of
     * each form, code points that decompose into marks alone or into several starters, marks that
     * compose and reorder, and Hangul: each the decompositions of its code points put together, and
     * composed or reordered across where they meet.
     */
    @Test
    void noTextComesToMoreThanTheLongestDecompositionTimesItsLength() {
        int[] alphabet = {'a', 0x1F82, 0xFDFA, 0x0344, 0xAC01, 0x1D160, 0xFB2C, 0x0301, 0x3300, 0x01D6,
            0x0323, 0xFB01, 0x0F73, 0x212B};
        List<String> texts = new ArrayList<>(List.of(""));
        for (int length = 1; length <= 3; length++) {
            List<String> longer = new ArrayList<>();
            for (String text : texts) {
                if (text.codePointCount(0, text.length()) == length - 1) {
                    for (int cp : alphabet) {
                        longer.add(text + Character.toString(cp));
                    }
                }
            }
            texts.addAll(longer);
        }
        for (String text : texts) {
            long length = count(text);
            for (Normalization.Form form : Normalization.Form.values()) {
                String normalized = Normalization.normalize(form, text);
                assertTrue(count(normalized) <= Normalization.longestDecomposition(form) * length,
                        () -> form + " " + text);
            }
        }
    }

    private static int count(String text) {
        return text.codePointCount(0, text.length());
    }
}
