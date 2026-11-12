package net.unit8.notation199x;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a case conversion says of how much longer its answer can be than the text is what the
 * conversion and the normalization answer: no code point comes to more, alone or at the end of a
 * cased run, and some code point comes to as much.
 *
 * <p>Asked of {@link CaseConversion} and {@link Normalization} as they answer, and not of the
 * tables the growth is generated beside, so that a growth worked out from the tables the wrong way
 * is not held to the same mistake.
 */
class ACaseConversionGrowsNoMoreThanItSaysTest {

    /** Texts whose answer grows, shrinks or composes, for the bound on a whole text. */
    private static final List<String> TEXTS = List.of(
            "", "abc", "straße", "İstanbul", "ΟΣ ΟΣΑ Ο'Σ", "ﬃﬃ", "ΐΰ", "ᾂᾂᾂ", "한글", "𐐀𐐨",
            "Á̂", "ŉİ");

    @Test
    void theLowercaseConversionGrowsAsMuchAsItSaysAndNoMore() {
        heldTo(CaseConversion.lowercaseGrowth(), CaseConversion::lowercase, "lowercase");
    }

    @Test
    void theUppercaseConversionGrowsAsMuchAsItSaysAndNoMore() {
        heldTo(CaseConversion.uppercaseGrowth(), CaseConversion::uppercase, "uppercase");
    }

    @Test
    void noTextsAnswerIsLongerThanItsGrowthSays() {
        for (String text : TEXTS) {
            long length = text.codePointCount(0, text.length());
            for (boolean lower : new boolean[] {true, false}) {
                CaseConversion.Growth growth =
                        lower ? CaseConversion.lowercaseGrowth() : CaseConversion.uppercaseGrowth();
                String answer = lower ? CaseConversion.lowercase(text) : CaseConversion.uppercase(text);
                assertTrue(count(answer) <= growth.mapped() * length, text);
                for (Normalization.Form form : List.of(Normalization.Form.NFC, Normalization.Form.NFD)) {
                    assertTrue(count(Normalization.normalize(form, answer)) <= growth.decomposed() * length,
                            form + " " + text);
                }
            }
        }
    }

    /**
     * Every code point, alone and after a cased one that ends no run, comes to no more than
     * {@code growth} says, and the most any comes to is what it says.
     */
    private static void heldTo(CaseConversion.Growth growth, UnaryOperator<String> conversion, String what) {
        int mapped = 0;
        int decomposed = 0;
        String before = "A";
        int beforeMapped = count(conversion.apply(before));
        int beforeDecomposed = count(Normalization.normalize(Normalization.Form.NFD, conversion.apply(before)));
        for (int cp = 0; cp <= 0x10FFFF; cp++) {
            if (cp >= 0xD800 && cp <= 0xDFFF) {
                continue;
            }
            String one = Character.toString(cp);
            String alone = conversion.apply(one);
            String after = conversion.apply(before + one);
            mapped = Math.max(mapped, Math.max(count(alone), count(after) - beforeMapped));
            decomposed = Math.max(decomposed, Math.max(
                    count(Normalization.normalize(Normalization.Form.NFD, alone)),
                    count(Normalization.normalize(Normalization.Form.NFD, after)) - beforeDecomposed));
        }
        assertEquals(new CaseConversion.Growth(mapped, decomposed), growth, what);
    }

    private static int count(String text) {
        return text.codePointCount(0, text.length());
    }
}
