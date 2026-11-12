package net.unit8.notation199x;

import org.junit.jupiter.api.Test;

import java.util.Random;

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
                if (cp >= 0xD800 && cp <= 0xDFFF) {
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

    @Test
    void noTextComesToMoreThanTheLongestDecompositionTimesItsLength() {
        int[] alphabet = {'a', 0x1F82, 0xFDFA, 0x0344, 0xAC01, 0x1D160, 0xFB2C, 0x0301, 0x3300, 0x01D6,
            0x0323, 0xFB01, 0x0F73, 0x212B};
        Random random = new Random(1999);
        for (int i = 0; i < 100_000; i++) {
            StringBuilder text = new StringBuilder();
            int length = random.nextInt(8);
            for (int j = 0; j < length; j++) {
                text.appendCodePoint(alphabet[random.nextInt(alphabet.length)]);
            }
            for (Normalization.Form form : Normalization.Form.values()) {
                String normalized = Normalization.normalize(form, text.toString());
                assertTrue(count(normalized) <= (long) Normalization.longestDecomposition(form) * length,
                        () -> form + " " + text);
            }
        }
    }

    private static int count(String text) {
        return text.codePointCount(0, text.length());
    }
}
