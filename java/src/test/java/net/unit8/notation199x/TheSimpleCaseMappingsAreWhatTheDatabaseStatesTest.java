package net.unit8.notation199x;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link CaseConversion#simpleUppercase} and {@link CaseConversion#simpleLowercase} are fields 12 and
 * 13 of {@code UnicodeData.txt}, read here from the file, over every code point: the code point
 * itself where the file names no mapping of it.
 */
class TheSimpleCaseMappingsAreWhatTheDatabaseStatesTest {

    @Test
    void theSimpleMappingsAreTheFieldsOfUnicodeData() throws IOException {
        Map<Integer, Integer> upper = new TreeMap<>();
        Map<Integer, Integer> lower = new TreeMap<>();
        for (String line : Files.readAllLines(Ucd.file("UnicodeData.txt"))) {
            if (line.isBlank()) {
                continue;
            }
            String[] fields = line.split(";", -1);
            int cp = Integer.parseInt(fields[0], 16);
            if (!fields[12].isEmpty()) {
                upper.put(cp, Integer.parseInt(fields[12], 16));
            }
            if (!fields[13].isEmpty()) {
                lower.put(cp, Integer.parseInt(fields[13], 16));
            }
        }
        for (int cp = 0; cp <= Character.MAX_CODE_POINT; cp++) {
            if (cp >= 0xD800 && cp <= 0xDFFF) {
                continue;
            }
            int asked = cp;
            assertEquals((int) upper.getOrDefault(cp, cp), CaseConversion.simpleUppercase(cp),
                    () -> "U+" + Integer.toHexString(asked));
            assertEquals((int) lower.getOrDefault(cp, cp), CaseConversion.simpleLowercase(cp),
                    () -> "U+" + Integer.toHexString(asked));
        }
        assertEquals('A', CaseConversion.simpleUppercase('a'));
        assertEquals(0x00DF, CaseConversion.simpleLowercase(0x1E9E));
    }
}
