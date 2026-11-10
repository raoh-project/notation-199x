package net.unit8.notation199x.pattern;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A meet holds the pairs of states a walk gets to, and is charged for those.
 *
 * <p>Two things are being said and they fail apart. The pairs the two sizes multiply to are what a
 * meet could hold, and a pair nothing leads to is a state made, stepped out of and paid for that no
 * string is ever in — so what is built is the walk's own. And the walk is over both kinds of step:
 * a pair one side leaves for nothing is reached by no symbol at all, and a growth that followed
 * only the labelled steps would come to a smaller machine accepting less.
 */
class AMeetIsMadeOfThePairsAWalkReachesTest {

    private static Automaton of(String regex, Meter meter) {
        PatternMeaning syntax =
                assertInstanceOf(PatternRead.Read.class, PatternParser.read(regex), regex).meaning();
        Automaton made = Automaton.of(syntax, meter);
        assertNotNull(made, regex);
        return made;
    }

    private static Meter roomy() {
        return new Meter(100_000, 10_000_000, 1_000_000_000L);
    }

    /**
     * Two machines whose labels never agree.
     *
     * <p>Every pair past the one a walk begins at is reached by a symbol both sides step over, and
     * there is none — so the meet is the beginning and nothing else, where the pairs of four states
     * against four are sixteen.
     */
    @Test
    void aPairNothingLeadsToIsNotMade() {
        Meter meter = roomy();
        Automaton left = of("[ab]{2}", meter);
        Automaton right = of("[cd]{2}", meter);
        assertEquals(16, left.size() * right.size(), "the pairs there are");

        Automaton met = left.and(right, meter);

        assertNotNull(met);
        assertEquals(1, met.size(), "and the pairs a walk gets to is the one it begins at");
        assertFalse(met.accepts("ab", roomy()), "nothing is accepted, which is what the meet holds");
    }

    /**
     * And a meet whose pairs would not have fit is built where the ones it reaches do.
     *
     * <p>The observable half of the same rule. What the two sizes multiply to is over what a
     * machine may hold and the walk reaches a handful, so a caller told this was not built would be
     * told it about an answer this compiler can afford — and the caller is a model somebody wrote.
     */
    @Test
    void aMeetIsRefusedOnWhatItReachesAndNotOnWhatItCouldReach() {
        // Room for either side and for what the walk reaches, and not for the pairs there are.
        Meter meter = new Meter(600, 2000, 1_000_000_000L);
        Automaton left = of("[ab]{30}", meter);
        Automaton right = of("[bc]{30}", meter);
        assertTrue(left.size() * right.size() > 600, "the pairs there are do not fit in a machine");

        Automaton met = left.and(right, meter);

        assertNotNull(met, "and the meet is built, being the pairs a walk reaches");
        assertTrue(met.accepts("b".repeat(30), roomy()), "and it holds what both sides hold");
    }

    /** And what a meet spends is the pairs it made, not the pairs it could have made. */
    @Test
    void aMeetIsChargedThePairsItReached() {
        Meter meter = roomy();
        Automaton left = of("[ab]{2}", meter);
        Automaton right = of("[cd]{2}", meter);
        int before = meter.left();

        assertNotNull(left.and(right, meter));

        assertEquals(1, before - meter.left(), "one pair reached is one state charged");
    }

    /**
     * A meet whose sides do agree, where the pairs reached are still fewer than the pairs there
     * are: the two walk in step, so all but the ones around the end are the same state on both
     * sides.
     */
    @Test
    void thePairsReachedAreTheWalksOwn() {
        Meter meter = roomy();
        Automaton left = of("[ab]{2}", meter);
        Automaton right = of("[bc]{2}", meter);

        Automaton met = left.and(right, meter);

        assertNotNull(met);
        assertEquals(6, met.size(), "six of the sixteen pairs are reached");
        assertTrue(met.accepts("bb", roomy()), "the one string both sides accept");
        assertFalse(met.accepts("ab", roomy()), "which the right side does not");
        assertFalse(met.accepts("bc", roomy()), "nor the left");
    }

    /**
     * The walk follows the steps that cost nothing as well.
     *
     * <p>Where the sides repeat different numbers of times, a pair is left by one side moving for
     * nothing while the other stays — so the pairs are not the ones both sides reach at the same
     * point in a string. A growth over the labelled steps alone stops early and the meet accepts
     * less than it holds.
     */
    @Test
    void aPairReachedByASideMovingForNothingIsMadeToo() {
        Meter meter = roomy();
        Automaton left = of("(aa)*", meter);
        Automaton right = of("(aaa)*", meter);

        Automaton met = left.and(right, meter);

        assertNotNull(met);
        assertTrue(met.accepts("", roomy()), "no letters at all is a run of both");
        assertTrue(met.accepts("aaaaaa", roomy()), "six is two threes and three twos");
        assertFalse(met.accepts("aa", roomy()), "which the right side does not accept");
        assertFalse(met.accepts("aaa", roomy()), "nor the left");
        assertFalse(met.accepts("aaaa", roomy()), "and four is neither");
    }

    /**
     * Where the pairs reached run to thousands, past every size the table of them begins at and is
     * made again at: each is one state and charged as one, and the pairs the walk found twice are
     * the one state both times.
     *
     * <p>Cycles of lengths with nothing in common reach every pair of their positions, which is
     * as many as the two lengths multiplied. A pair lost or made twice in the table would be a
     * state missing from the meet, or more charged than the meet holds.
     */
    @Test
    void manyPairsReachedAreEachOneStateAndChargedOnce() {
        Meter meter = roomy();
        Automaton left = of("(a{31})*", meter);
        Automaton right = of("(a{32})*", meter);
        int before = meter.left();

        Automaton met = left.and(right, meter);

        assertNotNull(met);
        int charged = before - meter.left();
        assertEquals(met.size(), charged, "a state charged for each pair, and none twice");
        assertTrue(charged > 512, "enough pairs to have made the table over more than once: " + charged);
        assertTrue(met.accepts("", roomy()));
        assertTrue(met.accepts("a".repeat(31 * 32), roomy()), "a run of both");
        assertTrue(met.accepts("a".repeat(31 * 32 * 2), roomy()), "and of both again");
        assertFalse(met.accepts("a".repeat(31 * 16), roomy()), "which the right side does not");
        assertFalse(met.accepts("a".repeat(32 * 15), roomy()), "nor the left");
        assertFalse(met.accepts("a".repeat(31 * 32 + 1), roomy()), "and one past is neither");
        Automaton again = left.and(right, meter);
        assertNotNull(again);
        assertEquals(met.shape(), again.shape(), "the same pairs are the same states every time");
    }

    /**
     * A meet is refused on the pairs it reaches however many there are, so a meter with room for
     * exactly those lets it through and one with a state less does not.
     */
    @Test
    void aMeetIsRefusedOnTheLastPairAndNotBefore() {
        Meter roomy = roomy();
        Automaton left = of("(a{31})*", roomy);
        Automaton right = of("(a{32})*", roomy);
        Automaton met = left.and(right, roomy);
        assertNotNull(met);
        int reached = met.size();

        assertNotNull(left.and(right, new Meter(reached, reached, 1_000_000_000L)));
        assertNull(left.and(right, new Meter(reached - 1, reached - 1, 1_000_000_000L)));
    }

    /**
     * Two big machines whose meet is one state cost that one state: nothing here is sized by what
     * the two multiply to, and a meet that reached a pair is charged one state however many there
     * could have been.
     */
    @Test
    void bigMachinesThatReachOnePairAreChargedOne() {
        Meter meter = roomy();
        Automaton left = of("[ab]{80}", meter);
        Automaton right = of("[cd]{80}", meter);
        assertTrue(left.size() * right.size() > 2048);
        int before = meter.left();

        Automaton met = left.and(right, meter);

        assertNotNull(met);
        assertEquals(1, met.size());
        assertEquals(1, before - meter.left());
    }
}
