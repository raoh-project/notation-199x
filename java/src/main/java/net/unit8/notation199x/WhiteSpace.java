package net.unit8.notation199x;

import java.util.ArrayList;
import java.util.List;
import net.unit8.notation199x.pattern.CodePoints;

/**
 * The code points with the Unicode {@code White_Space} property, as of Unicode 18.0.0: twenty-five
 * of them.
 *
 * <p>The set is generated from the database's {@code PropList.txt} rather than read off a platform
 * table, so a Unicode update in the JVM does not change it. It is neither
 * {@link Character#isWhitespace}, which leaves out the no-break spaces and takes in the four
 * information separators, nor what {@link String#strip} scans by.
 *
 * <p>A case conversion and a normalization keep the set apart from the rest. Each mapping of
 * {@link CaseConversion}, the one at the end of a cased run too, takes a code point that is
 * {@code White_Space} only to code points that are, and one that is not to code points at least one
 * of which is not. So does each code point's decomposition, the canonical one and the one by the
 * compatibility mappings as well. So a text that is all {@code White_Space} is all of it once
 * converted or normalized in any form, and a text that holds a code point that is not still holds
 * one. The generator checks both of every code point and stops where one does not hold.
 */
public final class WhiteSpace {

    private WhiteSpace() {}

    /**
     * Whether {@code codePoint} has the {@code White_Space} property.
     *
     * @param codePoint a code point
     * @return whether it has the property
     */
    public static boolean contains(int codePoint) {
        int[] starts = WhiteSpaceTables.WHITE_SPACE[0];
        int[] ends = WhiteSpaceTables.WHITE_SPACE[1];
        // A handful of ranges, so a scan in order is as quick as a search.
        for (int i = 0; i < starts.length && starts[i] <= codePoint; i++) {
            if (codePoint <= ends[i]) {
                return true;
            }
        }
        return false;
    }

    /**
     * The code points with the {@code White_Space} property, as ranges: those {@link #contains}
     * answers true for.
     *
     * @return the set
     */
    public static CodePoints codePoints() {
        return Held.CODE_POINTS;
    }

    /** {@link #codePoints}, made from {@link WhiteSpaceTables#WHITE_SPACE} when it is first asked. */
    private static final class Held {

        static final CodePoints CODE_POINTS = read();

        private static CodePoints read() {
            int[] starts = WhiteSpaceTables.WHITE_SPACE[0];
            int[] ends = WhiteSpaceTables.WHITE_SPACE[1];
            List<CodePoints.Range> ranges = new ArrayList<>();
            for (int i = 0; i < starts.length; i++) {
                ranges.add(new CodePoints.Range(starts[i], ends[i]));
            }
            return new CodePoints(ranges);
        }
    }
}
