package net.unit8.notation199x;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Two texts each in a form, joined and normalized, come to no more code points than the two and
 * {@link Normalization#seamGrowthBound}; in a decomposing form, to exactly the two.
 *
 * <p>Asked of {@link Normalization#normalize} as it answers. A text that comes out longer is a
 * starter that took in marks followed by marks that block them. So in a composing form each starter
 * that is its own normalization and took in a mark is followed by every mark that is its own
 * normalization, and by two marks of a set that holds every mark something composes with and one
 * mark of each combining class besides: two marks of one class that compose with nothing block and
 * are blocked alike. Every other starter that decomposes, normalized, is followed by one mark of
 * that set; in a decomposing form no mark composes, so the set there is a mark of each class.
 * Joined texts drawn at random are asked as well.
 */
class JoiningTwoNormalizedTextsAddsNoMoreThanTheSeamSaysTest {

    private static final List<Normalization.Form> FORMS = List.of(Normalization.Form.values());

    private static boolean composes(Normalization.Form form) {
        return form == Normalization.Form.NFC || form == Normalization.Form.NFKC;
    }

    /** Whether {@code text}, in {@code form}, holds a mark once decomposed in the form. */
    private static boolean tookInMarks(Normalization.Form form, String text) {
        return marksTakenIn(form, text) > 0;
    }

    /** How many marks {@code text}, in {@code form}, holds once decomposed in the form. */
    private static long marksTakenIn(Normalization.Form form, String text) {
        Normalization.Form decomposing = form == Normalization.Form.NFC || form == Normalization.Form.NFD
                ? Normalization.Form.NFD : Normalization.Form.NFKD;
        return Normalization.normalize(decomposing, text).codePoints()
                .filter(cp -> Normalization.combiningClass(cp) != 0).count();
    }

    @Test
    void theDecomposingFormsAddNothingAndTheComposingFormsTheMarksAStarterComposesWith() {
        assertEquals(0, Normalization.seamGrowthBound(Normalization.Form.NFD));
        assertEquals(0, Normalization.seamGrowthBound(Normalization.Form.NFKD));
        assertEquals(NormalizationTables.MOST_MARKS_COMPOSED, Normalization.seamGrowthBound(Normalization.Form.NFC));
        assertEquals(NormalizationTables.MOST_MARKS_COMPOSED, Normalization.seamGrowthBound(Normalization.Form.NFKC));
    }

    /**
     * A starter that took in as many marks as the bound followed by three marks: as many marks as
     * it can give back, put in order among them and blocking them by their classes.
     * The marks are those of {@link #representativeMarks}, three of them in a row that is its own
     * normalization.
     */
    @Test
    void aStarterThatTookInMarksFollowedByThreeMarksAddsNoMoreThanTheSeamSays() {
        for (Normalization.Form form : List.of(Normalization.Form.NFC, Normalization.Form.NFKC)) {
            List<String> marks = representativeMarks(form);
            List<String> triples = new ArrayList<>();
            for (String first : marks) {
                for (String second : marks) {
                    for (String third : marks) {
                        String joined = first + second + third;
                        if (Normalization.normalize(form, joined).equals(joined)) {
                            triples.add(joined);
                        }
                    }
                }
            }
            List<String> tookInMost = starters(form).stream()
                    .filter(starter -> marksTakenIn(form, starter) == NormalizationTables.MOST_MARKS_COMPOSED)
                    .toList();
            assertTrue(!tookInMost.isEmpty(), () -> form + ": no starter took in as many marks as the bound");
            for (String starter : tookInMost) {
                for (String triple : triples) {
                    heldTo(form, starter, triple);
                }
            }
        }
    }

    @Test
    void aStarterFollowedByOneMarkAddsNoMoreThanTheSeamSays() {
        for (Normalization.Form form : FORMS) {
            List<String> every = marks(form);
            List<String> representative = representativeMarks(form);
            int most = Integer.MIN_VALUE;
            for (String starter : starters(form)) {
                for (String mark : composes(form) && tookInMarks(form, starter) ? every : representative) {
                    most = Math.max(most, heldTo(form, starter, mark));
                }
            }
            assertEquals(composes(form) ? 1 : 0, most, form + ": the most one mark adds");
        }
    }

    @Test
    void aStarterThatTookInMarksFollowedByTwoMarksAddsNoMoreThanTheSeamSays() {
        for (Normalization.Form form : List.of(Normalization.Form.NFC, Normalization.Form.NFKC)) {
            List<String> marks = representativeMarks(form);
            List<String> pairs = new ArrayList<>();
            for (String first : marks) {
                for (String second : marks) {
                    String joined = first + second;
                    if (Normalization.normalize(form, joined).equals(joined)) {
                        pairs.add(joined);
                    }
                }
            }
            int most = Integer.MIN_VALUE;
            for (String starter : starters(form)) {
                if (tookInMarks(form, starter)) {
                    for (String pair : pairs) {
                        most = Math.max(most, heldTo(form, starter, pair));
                    }
                }
            }
            assertEquals(1, most, form + ": the most two marks add");
        }
    }

    @Test
    void joinedTextsDrawnAtRandomAddNoMoreThanTheSeamSays() {
        int[] alphabet = {'a', 'u', 'A', 'U', 0x01D5, 0x01D6, 0x1EE5, 0x0300, 0x0301, 0x0308, 0x0304, 0x0323,
            0x031B, 0x0345, 0x0313, 0x1F82, 0x1F00, 0x03B1, 0x0B47, 0x0B3E, 0x0B4B, 0x1100, 0x1161, 0x11A8,
            0xAC00, 0xAC01, 0xFB01, 0xFDFA, 0x3300, 0x0F73, 0x0344, 0x05E9, 0x05BC, 0x05C1, 0xFB2C, 0x1D160,
            0x0958, 0x093C, 0x0915, 0x00C5, 0x212B, 0x0327, 0x0338, 0x226E};
        Random random = new Random(199);
        for (int i = 0; i < 200_000; i++) {
            String left = draw(random, alphabet);
            String right = draw(random, alphabet);
            for (Normalization.Form form : FORMS) {
                heldTo(form, Normalization.normalize(form, left), Normalization.normalize(form, right));
            }
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

    /** The marks that are their own normalization in {@code form}. */
    private static List<String> marks(Normalization.Form form) {
        List<String> marks = new ArrayList<>();
        for (int cp = 0; cp <= Character.MAX_CODE_POINT; cp++) {
            if (isSurrogate(cp) || Normalization.combiningClass(cp) == 0) {
                continue;
            }
            String one = Character.toString(cp);
            if (Normalization.normalize(form, one).equals(one)) {
                marks.add(one);
            }
        }
        return marks;
    }

    /** Of {@link #marks}, the first of each combining class, and in a composing form every one that is
     *  the second of a two-member canonical decomposition. */
    private static List<String> representativeMarks(Normalization.Form form) {
        Set<String> seconds = new HashSet<>();
        for (int cp = 0; cp <= Character.MAX_CODE_POINT; cp++) {
            int[] mapped = isSurrogate(cp) ? null : Normalization.decomposition(cp, false);
            if (mapped != null && mapped.length == 2) {
                seconds.add(Character.toString(mapped[1]));
            }
        }
        SortedMap<Integer, String> byClass = new TreeMap<>();
        List<String> chosen = new ArrayList<>();
        for (String mark : marks(form)) {
            int markClass = Normalization.combiningClass(mark.codePointAt(0));
            if (composes(form) && seconds.contains(mark) || byClass.putIfAbsent(markClass, mark) == null) {
                chosen.add(mark);
            }
        }
        return chosen;
    }

    private static String draw(Random random, int[] alphabet) {
        StringBuilder text = new StringBuilder();
        int length = random.nextInt(6);
        for (int i = 0; i < length; i++) {
            text.appendCodePoint(alphabet[random.nextInt(alphabet.length)]);
        }
        return text.toString();
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
