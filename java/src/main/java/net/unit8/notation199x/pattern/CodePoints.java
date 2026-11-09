package net.unit8.notation199x.pattern;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.RandomAccess;

/**
 * A set of the characters a string is made of: Unicode scalar values, every code point but the
 * surrogates.
 *
 * <p>The universe is what text can hold, and not what a Java matcher could be handed. A matcher
 * reads half of a surrogate pair as a symbol of its own, but no text holds one — text is a sequence
 * of scalar values — so a surrogate is no symbol here, and nothing made of these can name one: a
 * range is never allowed to hold one, and the complement is taken within the scalar values. That is
 * what lets every sequence of symbols a machine here reads be a string.
 *
 * <p>Held as ranges, sorted and disjoint and never touching. Two spellings of one set would make
 * equal sets unequal, and what is written out of a reading has to come out the same each time it is
 * made — so the constructor normalises rather than trusting whoever built it.
 *
 * <p>The operations are the whole of what a character class is. A literal is one code point, a class
 * is a union of ranges, a negated class is the universe less that union, and {@code .} is the
 * universe less the five line terminators. None of them is a rule of its own here: they are all the
 * same algebra, which is what stops a reader having to know which shape a set came from.
 *
 * @param ranges the runs, in any order; they are held sorted and joined
 */
public record CodePoints(List<Range> ranges) {

    /** The greatest symbol there is. */
    public static final int LAST = 0x10FFFF;

    /** The first and the last surrogate, which are not symbols: the hole in the scalar values. */
    private static final int SURROGATES_FROM = 0xD800;
    private static final int SURROGATES_TO = 0xDFFF;

    /**
     * One run of symbols, both ends in it.
     *
     * <p>Never over a surrogate. A run that would span the hole is two runs, which is what
     * {@link #between} makes of it.
     *
     * @param from the first symbol of the run
     * @param to   the last symbol of the run
     */
    public record Range(int from, int to) {

        /** Holds a run to running from low to high inside the scalar values. */
        public Range {
            if (from < 0 || to > LAST || from > to) {
                throw new IllegalArgumentException("a run of symbols runs from low to high inside"
                        + " the universe: " + from + ".." + to);
            }
            if (from <= SURROGATES_TO && to >= SURROGATES_FROM) {
                throw new IllegalArgumentException("a surrogate is half of a pair and not a"
                        + " character, so no run of symbols holds one: " + from + ".." + to);
            }
        }

        /** Written out so that the hash of a list of runs can be worked out from their ends. */
        @Override
        public int hashCode() {
            return 31 * from + to;
        }
    }

    /**
     * Holds the runs sorted, joined where they touch, so that one set has one spelling.
     *
     * <p>What this class made itself is held already, and is not looked at again: the runs of a set
     * are a {@link Runs}, which only a way of building one that keeps them sorted and apart makes.
     */
    public CodePoints {
        ranges = ranges instanceof Runs ? ranges : normalised(ranges);
    }

    /** Nothing at all. */
    public static final CodePoints NONE = new CodePoints(List.of());

    /** Every symbol there is: the scalar values, the surrogates not among them. */
    public static final CodePoints EVERYTHING = new CodePoints(List.of(
            new Range(0, SURROGATES_FROM - 1), new Range(SURROGATES_TO + 1, LAST)));

    /**
     * Whether {@code codePoint} is a surrogate, which is no symbol.
     *
     * @param codePoint a code point
     * @return whether it is a surrogate
     */
    public static boolean isSurrogate(int codePoint) {
        return codePoint >= SURROGATES_FROM && codePoint <= SURROGATES_TO;
    }

    /**
     * Just this one, which is a scalar value: a surrogate handed here is a symbol nothing reads.
     *
     * @param symbol a scalar value
     * @return the set of it alone
     */
    public static CodePoints of(int symbol) {
        return new CodePoints(List.of(new Range(symbol, symbol)));
    }

    /**
     * What the five line terminators are, which is what a pattern's {@code .} leaves out.
     *
     * <p>Longer than the two a reader expects: a line feed, a carriage return, the next-line
     * character, and the two separators. {@code .} is the universe less these — and a negated class
     * is not, which is why neither is written as a rule and both are written as a difference.
     */
    public static final CodePoints LINE_TERMINATORS = of('\n').or(of('\r'))
            .or(of(0x85)).or(of(0x2028)).or(of(0x2029));

    /** What a pattern's {@code \d} holds: the ten ASCII digits and no other. */
    public static final CodePoints DIGITS = between('0', '9');

    /** What a pattern's {@code \w} holds: the ASCII letters, the ASCII digits and the underscore. */
    public static final CodePoints WORD = between('a', 'z').or(between('A', 'Z'))
            .or(DIGITS).or(of('_'));

    /**
     * What a pattern's {@code \s} holds: a space, a tab, a line feed, a vertical tab, a form feed
     * and a carriage return.
     *
     * <p>Not the {@code White_Space} set, which is what trimming reads. The two are separate sets the specification states separately, and a
     * pattern's shorthand is this one.
     */
    public static final CodePoints SPACES = of(' ').or(between('\t', '\r'));

    /**
     * Every symbol from one to another, both ends in it: the scalar values between them, so a run
     * across the surrogates leaves them out.
     *
     * <p>Both ends are symbols. A range whose end is a surrogate names a character that is not
     * one, and whoever read the pattern refuses it before this is asked.
     *
     * @param from the first symbol, a scalar value
     * @param to   the last symbol, a scalar value
     * @return the scalar values from one to the other
     */
    public static CodePoints between(int from, int to) {
        if (isSurrogate(from) || isSurrogate(to)) {
            throw new IllegalArgumentException("a surrogate is half of a pair and not a character,"
                    + " so it ends no run of symbols: " + from + ".." + to);
        }
        return scalars(from, to);
    }

    /**
     * Every symbol below {@code symbol}, which is the order symbols are compared in.
     *
     * @param symbol a scalar value
     * @return every scalar value below it
     */
    public static CodePoints below(int symbol) {
        if (symbol < 0 || symbol > LAST) {
            throw new IllegalArgumentException("no symbol is " + symbol);
        }
        return symbol > 0 ? scalars(0, symbol - 1) : NONE;
    }

    /**
     * The scalar values in {@code from..to}, as the one or two runs they make or none.
     *
     * <p>None only where the interval lies within the surrogates. An interval running backwards is
     * no interval at all, and is refused here rather than read as holding nothing: every way a run
     * is made comes through this or through {@link Range}, so a caller that got its ends the wrong
     * way round is told so whichever way it came.
     */
    private static CodePoints scalars(int from, int to) {
        if (from > to) {
            throw new IllegalArgumentException("a run of symbols runs from low to high: "
                    + from + ".." + to);
        }
        Ends out = new Ends(4);
        out.scalars(from, to);
        return out.made();
    }

    /**
     * Whether {@code symbol} is one of these.
     *
     * <p>A search and not a walk: the runs are in order and apart, and a machine asks this of every
     * label at every step it works out, so a class written wide would make each of those as long as
     * the class.
     *
     * @param symbol a code point
     * @return whether it is one of these
     */
    public boolean has(int symbol) {
        int[] ends = ends();
        int low = 0;
        int high = ends.length / 2 - 1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            if (ends[mid * 2 + 1] < symbol) {
                low = mid + 1;
            } else if (ends[mid * 2] > symbol) {
                high = mid - 1;
            } else {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether these hold no symbol.
     *
     * @return whether there is none
     */
    public boolean isEmpty() {
        return ranges.isEmpty();
    }

    /**
     * Whether these are every symbol there is.
     *
     * @return whether these are every scalar value
     */
    public boolean isEverything() {
        return ranges.equals(EVERYTHING.ranges);
    }

    /**
     * Either of them.
     *
     * @param other another set
     * @return the symbols in either
     */
    public CodePoints or(CodePoints other) {
        if (other.isEmpty() || this == other) {
            return this;
        }
        if (isEmpty()) {
            return other;
        }
        int[] a = ends();
        int[] b = other.ends();
        Ends out = new Ends(a.length + b.length);
        int i = 0;
        int j = 0;
        // Taken in the order they begin, and joined to the run before where they touch it.
        while (i < a.length || j < b.length) {
            if (j >= b.length || (i < a.length && a[i] <= b[j])) {
                out.joining(a[i], a[i + 1]);
                i += 2;
            } else {
                out.joining(b[j], b[j + 1]);
                j += 2;
            }
        }
        return out.made();
    }

    /**
     * Both of them.
     *
     * <p>Made in one pass over the runs of both, which are sorted and apart: at each step the
     * overlap of the two runs under the fingers, and then the finger whose run ends first moves on.
     * Every overlap is part of a run of each side, so none is over a surrogate, and two overlaps
     * that touched would mean two runs of one side touched. No complement is made, and nothing
     * written is sorted.
     *
     * @param other another set
     * @return the symbols in both
     */
    public CodePoints and(CodePoints other) {
        if (isEmpty() || other.isEmpty()) {
            return NONE;
        }
        if (this == other || ranges.equals(other.ranges)) {
            return this;
        }
        int[] a = ends();
        int[] b = other.ends();
        // Counted before it is written, so that what is written is one array of the size it is and
        // is never grown or cut down: the meeting of two small labels is most of what is asked.
        int overlaps = 0;
        for (int i = 0, j = 0; i < a.length && j < b.length; ) {
            int aTo = a[i + 1];
            int bTo = b[j + 1];
            if (Math.max(a[i], b[j]) <= Math.min(aTo, bTo)) {
                overlaps++;
            }
            if (aTo <= bTo) {
                i += 2;
            }
            if (bTo <= aTo) {
                j += 2;
            }
        }
        if (overlaps == 0) {
            return NONE;
        }
        int[] out = new int[overlaps * 2];
        int at = 0;
        for (int i = 0, j = 0; i < a.length && j < b.length; ) {
            int aTo = a[i + 1];
            int bTo = b[j + 1];
            int from = Math.max(a[i], b[j]);
            int to = Math.min(aTo, bTo);
            if (from <= to) {
                out[at++] = from;
                out[at++] = to;
            }
            // The run that ends first is done with; where both end together, both are.
            if (aTo <= bTo) {
                i += 2;
            }
            if (bTo <= aTo) {
                j += 2;
            }
        }
        return new CodePoints(new Runs(out));
    }

    /**
     * These, less those.
     *
     * <p>Made in one pass like {@link #and}: each run of these has the runs of {@code other} that
     * overlap it cut out. What is left of one run lies between runs that were cut out, so it is
     * apart from what is left of the next.
     *
     * @param other another set
     * @return the symbols in these and not in {@code other}
     */
    public CodePoints less(CodePoints other) {
        if (isEmpty() || other.isEmpty()) {
            return this;
        }
        if (this == other || ranges.equals(other.ranges)) {
            return NONE;
        }
        int[] a = ends();
        int[] b = other.ends();
        Ends out = new Ends(a.length + b.length);
        int j = 0;
        for (int i = 0; i < a.length; i += 2) {
            int next = a[i];
            while (j < b.length && b[j + 1] < next) {
                j += 2;
            }
            for (int k = j; k < b.length && b[k] <= a[i + 1]; k += 2) {
                if (b[k] > next) {
                    out.add(next, b[k] - 1);
                }
                next = b[k + 1] + 1;
            }
            if (next <= a[i + 1]) {
                out.add(next, a[i + 1]);
            }
        }
        return out.made();
    }

    /**
     * Every symbol these are not, which is a set of scalar values like any other.
     *
     * @return the scalar values these do not hold
     */
    public CodePoints not() {
        int[] a = ends();
        Ends out = new Ends(a.length + 4);
        int next = 0;
        for (int i = 0; i < a.length; i += 2) {
            if (a[i] > next) {
                out.scalars(next, a[i] - 1);
            }
            next = a[i + 1] + 1;
        }
        if (next <= LAST) {
            out.scalars(next, LAST);
        }
        return out.made();
    }

    /**
     * The least symbol in these, which is what a reading walks to first.
     *
     * <p>Asked only of a set that has one. What it is for is choosing the same value on two runs
     * over one model, and a set with nothing in it is one nothing is chosen from.
     *
     * @return the least symbol
     */
    public int least() {
        if (isEmpty()) {
            throw new IllegalStateException("nothing is the least of no symbols");
        }
        return ends()[0];
    }

    /**
     * How many symbols these hold, which a caller bounding its work asks.
     *
     * @return the number of symbols
     */
    public long size() {
        int[] ends = ends();
        long out = 0;
        for (int i = 0; i < ends.length; i += 2) {
            out += (long) ends[i + 1] - ends[i] + 1;
        }
        return out;
    }

    /**
     * The runs as pairs, the first symbol and then the last of each, in order.
     *
     * @return a copy, which the caller may write to
     */
    int[] pairs() {
        return ends().clone();
    }

    private int[] ends() {
        return ((Runs) ranges).ends;
    }

    /** The same runs, sorted and joined so that one set has one spelling. */
    private static List<Range> normalised(List<Range> given) {
        int size = given.size();
        long[] keys = new long[size];
        boolean inOrder = true;
        int last = -2;
        int at = 0;
        for (Range each : given) {
            keys[at++] = ((long) each.from() << 32) | each.to();
            // Out of order, overlapping or touching are all a run starting at or before the end of
            // the one before it, plus one.
            if (each.from() <= last) {
                inOrder = false;
            }
            last = each.to() + 1;
        }
        if (!inOrder) {
            Arrays.sort(keys);
        }
        Ends out = new Ends(size * 2);
        for (long key : keys) {
            // Touching as well as overlapping. `a..b` beside `b+1..c` is one run, and left as two
            // the same set would have two spellings and two of them would not be equal.
            out.joining((int) (key >> 32), (int) key);
        }
        return out.made().ranges;
    }

    @Override
    public String toString() {
        StringBuilder out = new StringBuilder("{");
        int[] ends = ends();
        for (int i = 0; i < ends.length; i += 2) {
            if (out.length() > 1) {
                out.append(' ');
            }
            out.append(String.format("%04X", ends[i]));
            if (ends[i + 1] != ends[i]) {
                out.append('-').append(String.format("%04X", ends[i + 1]));
            }
        }
        return out.append('}').toString();
    }

    /**
     * The runs of a set, held as the pairs of their ends.
     *
     * <p>Only ever made sorted, apart and not touching, by {@link Ends}; which is what lets the
     * constructor take one as it is. The hash is worked out once, since a set is a key of the maps
     * a machine is built in and is asked for it again for every step over it.
     */
    private static final class Runs extends AbstractList<Range> implements RandomAccess {

        private final int[] ends;
        private int hash;

        Runs(int[] ends) {
            this.ends = ends;
        }

        @Override
        public Range get(int index) {
            return new Range(ends[index * 2], ends[index * 2 + 1]);
        }

        @Override
        public int size() {
            return ends.length / 2;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            return other instanceof Runs it ? Arrays.equals(ends, it.ends) : super.equals(other);
        }

        @Override
        public int hashCode() {
            int out = hash;
            if (out == 0) {
                out = 1;
                for (int i = 0; i < ends.length; i += 2) {
                    out = 31 * out + (31 * ends[i] + ends[i + 1]);
                }
                hash = out;
            }
            return out;
        }
    }

    /** The ends of the runs of a set being made, in the order they are written. */
    private static final class Ends {

        private int[] held;
        private int length;

        Ends(int room) {
            this.held = new int[Math.max(room, 2)];
        }

        /** A run that begins after the one before ends and is not touching it. */
        void add(int from, int to) {
            if (length == held.length) {
                held = Arrays.copyOf(held, length * 2);
            }
            held[length++] = from;
            held[length++] = to;
        }

        /** A run that begins no earlier than the one before, joined to it where they meet. */
        void joining(int from, int to) {
            if (length > 0 && from <= held[length - 1] + 1) {
                if (to > held[length - 1]) {
                    held[length - 1] = to;
                }
            } else {
                add(from, to);
            }
        }

        /** The scalar values in {@code from..to}: the surrogates, which are no symbols, left out. */
        void scalars(int from, int to) {
            if (from < SURROGATES_FROM) {
                add(from, Math.min(to, SURROGATES_FROM - 1));
            }
            if (to > SURROGATES_TO) {
                add(Math.max(from, SURROGATES_TO + 1), to);
            }
        }

        CodePoints made() {
            return new CodePoints(new Runs(length == held.length ? held : Arrays.copyOf(held, length)));
        }
    }
}
