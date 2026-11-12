package net.unit8.notation199x;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What {@link WhiteSpace} says a case conversion and a decomposition keep of {@code White_Space}:
 * every code point is taken to code points all of which are {@code White_Space} where and only where
 * it is one, by each mapping of each conversion, the one at the end of a cased run too, and by its
 * canonical and its compatibility decomposition. Asked of the conversions and the normalization as
 * they answer, for each code point one of them takes to anything but itself.
 */
class WhiteSpaceIsKeptApartByConversionAndNormalizationTest {

    @Test
    void everyCodePointIsTakenToWhiteSpaceAloneWhereAndOnlyWhereItIsWhiteSpace() {
        String before = "A";
        String beforeLowered = CaseConversion.lowercase(before);
        for (int cp = 0; cp <= Character.MAX_CODE_POINT; cp++) {
            if (cp >= 0xD800 && cp <= 0xDFFF || isAnsweredAsItIs(cp)) {
                continue;
            }
            String one = Character.toString(cp);
            List<String> answers = new ArrayList<>(List.of(
                    text(CaseConversion.lowercaseMapping(cp)),
                    text(CaseConversion.uppercaseMapping(cp)),
                    CaseConversion.lowercase(one),
                    CaseConversion.uppercase(one),
                    Normalization.normalize(Normalization.Form.NFD, one),
                    Normalization.normalize(Normalization.Form.NFKD, one)));
            if (CaseConversion.lowercaseTurnsOnContext(cp)) {
                // At the end of a cased run.
                answers.add(CaseConversion.lowercase(before + one).substring(beforeLowered.length()));
            }
            boolean space = WhiteSpace.contains(cp);
            for (String answer : answers) {
                int asked = cp;
                assertEquals(space, answer.codePoints().allMatch(WhiteSpace::contains),
                        () -> "U+" + Integer.toHexString(asked) + " to " + answer);
            }
        }
    }

    /**
     * Whether every answer asked of {@code cp} is {@code cp}: both conversions leave it alone, which
     * {@link WhatACaseConversionLeavesAloneIsWhatItAnswersAsItIsTest} holds to what they answer, and
     * it has no decomposition, canonical or compatibility. Taken to itself, it is kept apart.
     */
    private static boolean isAnsweredAsItIs(int cp) {
        return CaseConversion.lowercaseLeavesAlone(cp) && CaseConversion.uppercaseLeavesAlone(cp)
                && Normalization.decomposeOne(cp, true) == null;
    }

    private static String text(int[] codePoints) {
        return new String(codePoints, 0, codePoints.length);
    }
}
