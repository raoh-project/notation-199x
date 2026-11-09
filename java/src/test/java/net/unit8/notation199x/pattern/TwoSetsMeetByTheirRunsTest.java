package net.unit8.notation199x.pattern;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The meeting of two sets is made by walking their runs once, and says what the complement form
 * says: the symbols in both. The runs it writes are parts of runs that were apart, so they come out
 * sorted, apart and not touching without being sorted again.
 */
class TwoSetsMeetByTheirRunsTest {

    private static CodePoints of(int... ends) {
        List<CodePoints.Range> runs = new ArrayList<>();
        for (int i = 0; i < ends.length; i += 2) {
            runs.add(new CodePoints.Range(ends[i], ends[i + 1]));
        }
        return new CodePoints(runs);
    }

    @Test
    void nothingMeetsAnythingInNothing() {
        CodePoints x = of(1, 5, 10, 20);
        assertSame(CodePoints.NONE, CodePoints.NONE.and(x));
        assertSame(CodePoints.NONE, x.and(CodePoints.NONE));
    }

    @Test
    void aSetMeetingItselfIsItself() {
        CodePoints x = of(1, 5, 10, 20);
        assertSame(x, x.and(x));
        assertSame(x, x.and(of(1, 5, 10, 20)));
    }

    @Test
    void setsApartMeetInNothing() {
        assertEquals(CodePoints.NONE, of(1, 5).and(of(6, 9)));
        assertEquals(CodePoints.NONE, of(1, 5, 20, 30).and(of(7, 8, 40, 50)));
    }

    @Test
    void aRunWithinAnotherIsMetAsItself() {
        assertEquals(of(3, 4), of(1, 10).and(of(3, 4)));
        assertEquals(of(3, 4), of(3, 4).and(of(1, 10)));
    }

    @Test
    void runsThatOverlapInPartMeetInThePart() {
        assertEquals(of(5, 7), of(1, 7).and(of(5, 10)));
        assertEquals(of(5, 7), of(5, 10).and(of(1, 7)));
        assertEquals(of(5, 7, 20, 22), of(1, 7, 20, 30).and(of(5, 10, 15, 22)));
    }

    @Test
    void runsThatEndTogetherBothMoveOn() {
        assertEquals(of(3, 5, 8, 9), of(1, 5, 8, 9).and(of(3, 5, 7, 9)));
    }

    @Test
    void runsThatTouchAreJoinedWhereTheyAreMade() {
        assertEquals(of(1, 10), of(1, 5, 6, 10));
    }

    @Test
    void aMeetingAcrossTheSurrogatesHoldsNone() {
        CodePoints across = of(0xD7FE, 0xD7FF, 0xE000, 0xE001);
        assertEquals(across, across.and(CodePoints.EVERYTHING));
        assertEquals(of(0xD7FF, 0xD7FF, 0xE000, 0xE000),
                across.and(CodePoints.between(0xD7FF, 0xE000)));
        for (int surrogate = 0xD800; surrogate <= 0xDFFF; surrogate += 0x7F) {
            assertFalse(across.and(CodePoints.EVERYTHING).has(surrogate));
        }
    }

    @Test
    void everythingMeetsASetInTheSet() {
        CodePoints x = of(0, 5, 0x10, 0x20, 0xE000, 0x10FFFF);
        assertEquals(x, CodePoints.EVERYTHING.and(x));
        assertEquals(x, x.and(CodePoints.EVERYTHING));
    }

    @Test
    void theRunsOfARunCannotBeChangedFromOutside() {
        List<CodePoints.Range> given = new ArrayList<>(List.of(new CodePoints.Range(1, 5)));
        CodePoints x = new CodePoints(given);
        given.add(new CodePoints.Range(100, 200));
        assertEquals(of(1, 5), x);
        assertEquals(of(1, 5), x.and(CodePoints.EVERYTHING));
    }

    @Test
    void aSpellingGivenOutOfOrderIsStillNormalised() {
        assertEquals(of(1, 10), of(6, 10, 1, 5));
        assertEquals(of(1, 10), of(1, 7, 5, 10));
        assertEquals(of(1, 3, 7, 9), of(7, 9, 1, 3));
    }

    @Test
    void randomPairsMeetAsComplementsSayAndAsMembershipSays() {
        Random random = new Random(199);
        for (int round = 0; round < 2000; round++) {
            CodePoints a = random(random);
            CodePoints b = random(random);
            CodePoints met = a.and(b);

            assertEquals(a.not().or(b.not()).not(), met, a + " and " + b);
            CodePoints either = a.or(b);
            CodePoints less = a.less(b);
            CodePoints not = a.not();
            assertEquals(a.and(b.not()), less);
            assertEquals(a.not().and(b.not()).not(), either);
            assertNormal(met);
            assertNormal(either);
            assertNormal(less);
            assertNormal(not);
            assertEquals(a, not.not());
            assertEquals(a.hashCode(), new CodePoints(new ArrayList<>(a.ranges())).hashCode());

            for (int symbol : probes(random, a, b)) {
                String where = Integer.toHexString(symbol) + " in " + a + " and " + b;
                assertEquals(a.has(symbol) && b.has(symbol), met.has(symbol), where);
                assertEquals(a.has(symbol) || b.has(symbol), either.has(symbol), where);
                assertEquals(a.has(symbol) && !b.has(symbol), less.has(symbol), where);
                assertEquals(!CodePoints.isSurrogate(symbol) && !a.has(symbol), not.has(symbol),
                        where);
            }
        }
    }

    private static void assertNormal(CodePoints set) {
        CodePoints.Range previous = null;
        for (CodePoints.Range each : set.ranges()) {
            if (previous != null) {
                assertTrue(each.from() > previous.to() + 1, set.toString());
            }
            previous = each;
        }
    }

    private static final int[] EDGES = {0, 0xD7FF, 0xE000, 0x10FFFF};

    private static int edgy(Random random) {
        if (random.nextInt(3) == 0) {
            int edge = EDGES[random.nextInt(EDGES.length)] + random.nextInt(5) - 2;
            return Math.max(0, Math.min(CodePoints.LAST, edge));
        }
        return random.nextInt(40);
    }

    private static CodePoints random(Random random) {
        List<CodePoints.Range> runs = new ArrayList<>();
        int count = random.nextInt(6);
        for (int i = 0; i < count; i++) {
            int one = edgy(random);
            int other = edgy(random);
            CodePoints run = CodePoints.between(skipSurrogate(Math.min(one, other)),
                    skipSurrogate(Math.max(one, other)));
            runs.addAll(run.ranges());
        }
        return new CodePoints(runs);
    }

    private static int skipSurrogate(int symbol) {
        return CodePoints.isSurrogate(symbol) ? 0xE000 : symbol;
    }

    private static List<Integer> probes(Random random, CodePoints a, CodePoints b) {
        List<Integer> out = new ArrayList<>();
        for (CodePoints set : List.of(a, b)) {
            for (CodePoints.Range each : set.ranges()) {
                out.add(each.from());
                out.add(each.to());
                if (each.from() > 0) {
                    out.add(each.from() - 1);
                }
                if (each.to() < CodePoints.LAST) {
                    out.add(each.to() + 1);
                }
            }
        }
        for (int i = 0; i < 20; i++) {
            out.add(random.nextInt(60));
        }
        return out;
    }
}
