import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * The primary compositions laid out as the pairs of code points that compose, for the tables that
 * read a pair in steps: the row of the first member, and in it the column of the second.
 *
 * <p>What composes is a relation the model states, {@link UcdModel.NormalizationDerived#compositions};
 * this is how an emitter holds it, as the layout of {@link ThreeLevelTable} is how it holds a value
 * for each code point, and so the numbers and the limits of the layout are here and not in the model.
 * A reader of a language reads the column from the low {@link #COLUMN_BITS} bits of a pair, and the
 * composite from the rest.
 *
 * @param firsts    for each code point, 0 where it is the first member of no composition, and
 *                  otherwise one more than its row: where it is among the first members, in order
 *                  of code point
 * @param seconds   the same of the second members, by column. A code point is not 0 here where
 *                  and only where a starter before it may compose with it by the table
 * @param rowStarts where each row begins among {@code pairs}, by row number less 1, and
 *                  {@code pairs.length} after the last: the pairs of a row are
 *                  {@code pairs[rowStarts[row]..rowStarts[row + 1])}
 * @param pairs     the pairs that compose, a row at a time and in order of column within a row, each
 *                  the composite's code point shifted left by {@link #COLUMN_BITS} and the column of
 *                  the second member, counted from 1 as {@code seconds} counts it
 */
record SparseComposition(PagedTable firsts, PagedTable seconds, int[] rowStarts, int[] pairs) {

    /** How many low bits of a pair hold the column. A column counted from 1 has to fit them, so a
     *  Unicode version with more second members than that is a change of this and of each reader. */
    static final int COLUMN_BITS = 8;

    /** The greatest row start there is room for: an emitter holds them in sixteen bits, a {@code u16}
     *  or a {@code uint16} or a code unit of a string, which is as many pairs as there are. */
    static final int MOST_PAIRS = 0xFFFF;

    /**
     * The layout of {@code compositions}.
     *
     * @param compositions each primary composite, by the two code points it is composed from
     * @return the layout, which holds each of them once
     */
    static SparseComposition of(UcdModel.CodePointMapping compositions) {
        SortedSet<Integer> firsts = new TreeSet<>();
        SortedSet<Integer> seconds = new TreeSet<>();
        for (int[] pair : compositions.entries().values()) {
            firsts.add(pair[0]);
            seconds.add(pair[1]);
        }
        Map<Integer, Integer> rows = numbered(firsts);
        Map<Integer, Integer> columns = numbered(seconds);
        if (columns.size() >= 1 << COLUMN_BITS) {
            throw new IllegalStateException(columns.size() + " second members of a composition do not fit the "
                    + COLUMN_BITS + " bits of a column, counted from 1");
        }
        if (compositions.entries().size() > MOST_PAIRS) {
            throw new IllegalStateException(compositions.entries().size() + " compositions do not fit the "
                    + "sixteen bits of the start of a row");
        }
        // For each row, the composite of each column, in order of column.
        List<SortedMap<Integer, Integer>> byRow = new ArrayList<>();
        for (int row = 0; row < rows.size(); row++) {
            byRow.add(new TreeMap<>());
        }
        compositions.entries().forEach((composite, pair) -> {
            if (composite >>> (Integer.SIZE - COLUMN_BITS) != 0) {
                throw new IllegalStateException("U+" + UcdModel.hex(composite) + " does not fit above the "
                        + COLUMN_BITS + " bits of a column in the 32 bits of a pair");
            }
            Integer other = byRow.get(rows.get(pair[0])).put(columns.get(pair[1]) + 1, composite);
            if (other != null) {
                throw new IllegalStateException("U+" + UcdModel.hex(pair[0]) + " and U+" + UcdModel.hex(pair[1])
                        + " compose to both U+" + UcdModel.hex(other) + " and U+" + UcdModel.hex(composite)
                        + ", where a pair holds one composite");
            }
        });
        int[] rowStarts = new int[rows.size() + 1];
        int[] pairs = new int[compositions.entries().size()];
        int at = 0;
        for (int row = 0; row < rows.size(); row++) {
            rowStarts[row] = at;
            for (Map.Entry<Integer, Integer> cell : byRow.get(row).entrySet()) {
                pairs[at++] = cell.getValue() << COLUMN_BITS | cell.getKey();
            }
        }
        rowStarts[rows.size()] = at;
        return new SparseComposition(PagedTable.of(cp -> rows.containsKey(cp) ? rows.get(cp) + 1 : 0),
                PagedTable.of(cp -> columns.containsKey(cp) ? columns.get(cp) + 1 : 0), rowStarts, pairs);
    }

    /** Each of {@code codePoints} by where it is among them, from 0. */
    private static Map<Integer, Integer> numbered(SortedSet<Integer> codePoints) {
        Map<Integer, Integer> numbered = new TreeMap<>();
        for (int cp : codePoints) {
            numbered.put(cp, numbered.size());
        }
        return numbered;
    }
}
