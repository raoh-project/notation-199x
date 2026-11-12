package net.unit8.notation199x;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Two texts each in a form, joined and normalized, come to no more code points than the two and
 * {@link Normalization#seamGrowthBound}; in a decomposing form, to exactly the two.
 *
 * <p>Asked of {@link Normalization#normalize} as it answers. Canonical order moves no mark past a
 * starter, and a starter composes only with a starter right before it, so what the seam can change
 * is the last starter of the first text, the marks it took in, and what the second text begins
 * with: marks, or a starter that composes with it. Each case is asked once, and no case twice that
 * comes out the same way:
 *
 * <ul>
 *   <li>Each starter that decomposes in the form, normalized in it, is followed by rows of the
 *       marks that tell apart what it comes to ({@link #marksThatTellApart}), as many as it took in
 *       and at least one.</li>
 *   <li>Each starter is followed by each starter it composes with.</li>
 * </ul>
 */
class JoiningTwoNormalizedTextsAddsNoMoreThanTheSeamSaysTest {

    private static final List<Normalization.Form> FORMS = List.of(Normalization.Form.values());

    private static boolean composes(Normalization.Form form) {
        return form == Normalization.Form.NFC || form == Normalization.Form.NFKC;
    }

    private static Normalization.Form decomposing(Normalization.Form form) {
        return form == Normalization.Form.NFC || form == Normalization.Form.NFD
                ? Normalization.Form.NFD : Normalization.Form.NFKD;
    }

    @Test
    void theDecomposingFormsAddNothingAndTheComposingFormsTheMarksAStarterComposesWith() {
        assertEquals(0, Normalization.seamGrowthBound(Normalization.Form.NFD));
        assertEquals(0, Normalization.seamGrowthBound(Normalization.Form.NFKD));
        assertEquals(NormalizationTables.MOST_MARKS_COMPOSED, Normalization.seamGrowthBound(Normalization.Form.NFC));
        assertEquals(NormalizationTables.MOST_MARKS_COMPOSED, Normalization.seamGrowthBound(Normalization.Form.NFKC));
    }

    /**
     * A starter that took in marks gives back no more than it took in, so it is followed by rows of
     * up to that many marks, put in order among the ones it gives back and blocking them by their
     * classes; one that took in none is followed by one mark. A starter that took in as many marks
     * as the bound is among them, and so is U+01D6, which U+0323 makes one longer.
     */
    @Test
    void aStarterFollowedByTheMarksThatTellApartWhatItComesToAddsNoMoreThanTheSeamSays() {
        Map<Integer, Map<Integer, Integer>> withAMark = compositionsWithAMark();
        for (Normalization.Form form : FORMS) {
            SortedMap<Integer, List<Integer>> byClass = marksByClass(form);
            int most = Integer.MIN_VALUE;
            int mostTakenIn = 0;
            for (String starter : starters(form)) {
                int[] decomposed = Normalization.normalize(decomposing(form), starter).codePoints().toArray();
                // Some starters decompose into marks alone (U+0F73), and then there is no last starter.
                int last = decomposed.length - 1;
                while (last >= 0 && Normalization.combiningClass(decomposed[last]) != 0) {
                    last--;
                }
                int takenIn = decomposed.length - 1 - last;
                mostTakenIn = Math.max(mostTakenIn, takenIn);
                List<Integer> marks = marksThatTellApart(form, decomposed, last, byClass, withAMark);
                for (String row : rows(marks, Math.max(1, takenIn))) {
                    most = Math.max(most, heldTo(form, starter, row));
                }
            }
            if (composes(form)) {
                assertEquals(NormalizationTables.MOST_MARKS_COMPOSED, mostTakenIn, form + ": the most marks taken in");
            }
            assertEquals(composes(form) ? 1 : 0, most, form + ": the most marks add");
        }
    }

    /** A starter followed by a starter it composes with: by a composition of the tables, and by
     *  Hangul's L+V and LV+T. */
    @Test
    void aStarterFollowedByAStarterItComposesWithAddsNoMoreThanTheSeamSays() {
        List<int[]> pairs = new ArrayList<>();
        for (int cp = 0; cp <= Character.MAX_CODE_POINT; cp++) {
            int[] mapped = isSurrogate(cp) ? null : Normalization.decomposition(cp, false);
            if (mapped != null && mapped.length == 2 && Normalization.combiningClass(mapped[1]) == 0
                    && Normalization.compose(mapped[0], mapped[1]) == cp) {
                pairs.add(mapped);
            }
        }
        for (int l = 0x1100; l < 0x1100 + 19; l++) {
            for (int v = 0x1161; v < 0x1161 + 21; v++) {
                pairs.add(new int[] {l, v});
                int lv = Normalization.compose(l, v);
                for (int t = 0x11A8; t < 0x11A7 + 28; t++) {
                    pairs.add(new int[] {lv, t});
                }
            }
        }
        for (Normalization.Form form : List.of(Normalization.Form.NFC, Normalization.Form.NFKC)) {
            int asked = 0;
            for (int[] pair : pairs) {
                String first = Character.toString(pair[0]);
                String second = Character.toString(pair[1]);
                if (Normalization.normalize(form, first).equals(first)
                        && Normalization.normalize(form, second).equals(second)) {
                    assertTrue(heldTo(form, first, second) < 0, () -> form + " " + hex(first + second));
                    asked++;
                }
            }
            assertTrue(asked > 19 * 21 * 28, form.toString());
        }
    }

    /** Holds {@code a} and {@code b}, each in {@code form}, to the bound; answers how many code points
     *  normalizing them joined adds. */
    private static int heldTo(Normalization.Form form, String a, String b) {
        int added = count(Normalization.normalize(form, a + b)) - count(a) - count(b);
        // Written out only for a pair that fails: most of the time asking a pair takes otherwise.
        Supplier<String> what = () -> form + " " + hex(a) + " + " + hex(b);
        assertTrue(added <= Normalization.seamGrowthBound(form), () -> what.get() + " adds " + added);
        if (Normalization.seamGrowthBound(form) == 0) {
            assertEquals(0, added, what);
        }
        return added;
    }

    /**
     * The marks that tell apart what the last starter of {@code decomposed}, at {@code last} (-1
     * where it holds none), and the marks after it come to in {@code form} when marks follow.
     *
     * <p>Every mark that composes with that starter, or with what it composes into with marks one
     * after another, since each makes another composite. Of the marks that compose with none of
     * them, what they do is be put in order and block, and both turn on how a class stands against
     * the classes of the marks the starter took in and of the marks that compose: so one mark of
     * each of those classes, and one of a class between each two of them, below the least and above
     * the greatest. In a decomposing form no mark composes.
     */
    private static List<Integer> marksThatTellApart(Normalization.Form form, int[] decomposed, int last,
                                                    SortedMap<Integer, List<Integer>> byClass,
                                                    Map<Integer, Map<Integer, Integer>> withAMark) {
        Set<Integer> composing = new LinkedHashSet<>();
        if (composes(form) && last >= 0) {
            Deque<Integer> reached = new ArrayDeque<>(List.of(decomposed[last]));
            Set<Integer> seen = new HashSet<>(reached);
            while (!reached.isEmpty()) {
                withAMark.getOrDefault(reached.pop(), Map.of()).forEach((mark, composite) -> {
                    composing.add(mark);
                    if (seen.add(composite)) {
                        reached.push(composite);
                    }
                });
            }
        }
        NavigableSet<Integer> classes = new TreeSet<>();
        for (int i = last + 1; i < decomposed.length; i++) {
            classes.add(Normalization.combiningClass(decomposed[i]));
        }
        composing.forEach(mark -> classes.add(Normalization.combiningClass(mark)));
        List<Integer> marks = new ArrayList<>(composing);
        Set<Integer> standings = new HashSet<>();
        byClass.forEach((markClass, ofTheClass) -> {
            // A class stands against the others as itself where it is one of them, and otherwise
            // by the least of them above it, or above them all.
            Integer above = classes.ceiling(markClass);
            int standing = above == null ? 256 : above.equals(markClass) ? markClass : 512 + above;
            if (standings.add(standing)) {
                ofTheClass.stream().filter(mark -> !composing.contains(mark)).findFirst().ifPresent(marks::add);
            }
        });
        return marks;
    }

    /** Every row of one up to {@code longest} of {@code marks} whose classes never go down: the rows
     *  that are their own normalization, since marks with no starter before them compose with none. */
    private static List<String> rows(List<Integer> marks, int longest) {
        List<String> rows = new ArrayList<>();
        addRows(marks, longest, new StringBuilder(), 0, rows);
        return rows;
    }

    private static void addRows(List<Integer> marks, int longest, StringBuilder row, int leastClass,
                                List<String> rows) {
        if (longest == 0) {
            return;
        }
        for (int mark : marks) {
            int markClass = Normalization.combiningClass(mark);
            if (markClass >= leastClass) {
                int length = row.length();
                row.appendCodePoint(mark);
                rows.add(row.toString());
                addRows(marks, longest - 1, row, markClass, rows);
                row.setLength(length);
            }
        }
    }

    /** For each code point a mark composes with, each such mark and what the two compose into. */
    private static Map<Integer, Map<Integer, Integer>> compositionsWithAMark() {
        Map<Integer, Map<Integer, Integer>> withAMark = new HashMap<>();
        for (int cp = 0; cp <= Character.MAX_CODE_POINT; cp++) {
            int[] mapped = isSurrogate(cp) ? null : Normalization.decomposition(cp, false);
            if (mapped != null && mapped.length == 2 && Normalization.combiningClass(mapped[1]) != 0
                    && Normalization.compose(mapped[0], mapped[1]) == cp) {
                withAMark.computeIfAbsent(mapped[0], first -> new TreeMap<>()).put(mapped[1], cp);
            }
        }
        return withAMark;
    }

    /** Each starter that decomposes in {@code form}, normalized in it: in a composing form only those
     *  that are their own normalization. */
    private static Set<String> starters(Normalization.Form form) {
        boolean compatibility = form == Normalization.Form.NFKC || form == Normalization.Form.NFKD;
        Set<String> starters = new LinkedHashSet<>();
        for (int cp = 0; cp <= Character.MAX_CODE_POINT; cp++) {
            if (isSurrogate(cp) || Normalization.combiningClass(cp) != 0
                    || Normalization.decomposeOne(cp, compatibility) == null) {
                continue;
            }
            String one = Character.toString(cp);
            String normalized = Normalization.normalize(form, one);
            if (!composes(form) || normalized.equals(one)) {
                starters.add(normalized);
            }
        }
        return starters;
    }

    private static boolean isSurrogate(int cp) {
        return cp >= 0xD800 && cp <= 0xDFFF;
    }

    /** The marks that are their own normalization in {@code form}, by combining class. */
    private static SortedMap<Integer, List<Integer>> marksByClass(Normalization.Form form) {
        SortedMap<Integer, List<Integer>> byClass = new TreeMap<>();
        for (int cp = 0; cp <= Character.MAX_CODE_POINT; cp++) {
            int markClass = isSurrogate(cp) ? 0 : Normalization.combiningClass(cp);
            if (markClass != 0 && Normalization.normalize(form, Character.toString(cp)).equals(Character.toString(cp))) {
                byClass.computeIfAbsent(markClass, ofTheClass -> new ArrayList<>()).add(cp);
            }
        }
        return byClass;
    }

    private static int count(String text) {
        return text.codePointCount(0, text.length());
    }

    private static String hex(String text) {
        StringBuilder out = new StringBuilder();
        text.codePoints().forEach(cp -> out.append(out.isEmpty() ? "" : " ")
                .append(String.format(Locale.ROOT, "U+%04X", cp)));
        return out.toString();
    }
}
