import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The values of a {@link PagedTable} laid out so that one is found in three steps, with what repeats
 * held once at every step: the code space is cut into blocks of {@code 1 << blockShift} code points
 * and each block into pages of {@code 1 << pageShift}; the top stage gives each block the number of
 * its run of page numbers, the middle stage holds those runs, and the leaf stage holds the pages. A
 * run is held once however many blocks have it, and so is a page however many runs name it.
 *
 * <p>This says what a {@link PagedTable} says, differently placed. The sizes of a block and of a
 * page are chosen, per table, as the pair that holds the table in the fewest bytes.
 *
 * @param blockShift how many bits of a code point pick a place within a block
 * @param pageShift  how many bits of a code point pick a place within a page
 * @param top        the run of each block, by {@code cp >>> blockShift}
 * @param middle     the runs one after another, each {@code 1 << (blockShift - pageShift)} page
 *                   numbers long
 * @param leaf       the pages one after another, each {@code 1 << pageShift} values long
 */
record ThreeLevelTable(int blockShift, int pageShift, int[] top, int[] middle, int[] leaf) {

    private static final int CODE_POINTS = Character.MAX_CODE_POINT + 1;

    /**
     * The layout of {@code table} that takes the fewest bytes, where a value is {@code valueBytes}
     * bytes and a number of a run or of a page is as many as its greatest needs. Of layouts that take
     * as many, the one with the larger page is taken.
     *
     * @param table      what to hold
     * @param valueBytes how many bytes a value is written in, 1 or 2
     * @return the table, which answers what {@code table} does for every code point
     */
    static ThreeLevelTable of(PagedTable table, int valueBytes) {
        int[] values = new int[CODE_POINTS];
        for (int cp = 0; cp < CODE_POINTS; cp++) {
            values[cp] = table.get(cp);
        }
        ThreeLevelTable best = null;
        long fewest = Long.MAX_VALUE;
        // 0x110000 is 17 * 2^16, so every block of up to 2^16 code points is whole.
        for (int blockShift = 2; blockShift <= 16; blockShift++) {
            for (int pageShift = 1; pageShift < blockShift; pageShift++) {
                if (blockShift - pageShift > 12) {
                    continue;
                }
                ThreeLevelTable candidate = layout(values, blockShift, pageShift);
                long bytes = candidate.bytes(valueBytes);
                if (bytes >= 0 && (bytes < fewest || bytes == fewest && pageShift > best.pageShift)) {
                    best = candidate;
                    fewest = bytes;
                }
            }
        }
        if (best == null) {
            throw new IllegalStateException("no layout holds the table in the numbers that can be written");
        }
        for (int cp = 0; cp < CODE_POINTS; cp++) {
            if (best.get(cp) != values[cp]) {
                throw new IllegalStateException("the table of U+" + UcdModel.hex(cp) + " is not its value");
            }
        }
        return best;
    }

    private static ThreeLevelTable layout(int[] values, int blockShift, int pageShift) {
        int pageSize = 1 << pageShift;
        int runLength = 1 << (blockShift - pageShift);
        Map<List<Integer>, Integer> pageNumbers = new HashMap<>();
        List<int[]> pages = new ArrayList<>();
        int[] pageOf = new int[CODE_POINTS >>> pageShift];
        for (int page = 0; page < pageOf.length; page++) {
            int[] contents = Arrays.copyOfRange(values, page * pageSize, (page + 1) * pageSize);
            List<Integer> key = Arrays.stream(contents).boxed().toList();
            Integer number = pageNumbers.get(key);
            if (number == null) {
                number = pages.size();
                pageNumbers.put(key, number);
                pages.add(contents);
            }
            pageOf[page] = number;
        }
        Map<List<Integer>, Integer> runNumbers = new HashMap<>();
        List<int[]> runs = new ArrayList<>();
        int[] top = new int[CODE_POINTS >>> blockShift];
        for (int block = 0; block < top.length; block++) {
            int[] run = Arrays.copyOfRange(pageOf, block * runLength, (block + 1) * runLength);
            List<Integer> key = Arrays.stream(run).boxed().toList();
            Integer number = runNumbers.get(key);
            if (number == null) {
                number = runs.size();
                runNumbers.put(key, number);
                runs.add(run);
            }
            top[block] = number;
        }
        return new ThreeLevelTable(blockShift, pageShift, top, join(runs, runLength), join(pages, pageSize));
    }

    private static int[] join(List<int[]> parts, int length) {
        int[] joined = new int[parts.size() * length];
        for (int i = 0; i < parts.size(); i++) {
            System.arraycopy(parts.get(i), 0, joined, i * length, length);
        }
        return joined;
    }

    /** The value of {@code cp}. */
    int get(int cp) {
        int run = top[cp >>> blockShift];
        int page = middle[run << (blockShift - pageShift) | (cp >>> pageShift) & ((1 << (blockShift - pageShift)) - 1)];
        return leaf[page << pageShift | cp & ((1 << pageShift) - 1)];
    }

    /** How many bytes the three stages take, or -1 where a number does not fit in two bytes. */
    long bytes(int valueBytes) {
        int runBytes = numberBytes(top);
        int pageBytes = numberBytes(middle);
        if (runBytes < 0 || pageBytes < 0) {
            return -1;
        }
        return (long) top.length * runBytes + (long) middle.length * pageBytes + (long) leaf.length * valueBytes;
    }

    /** How many bytes the greatest of {@code numbers} is written in, 1 or 2, or -1 where it is more. */
    int numberBytes(int[] numbers) {
        int greatest = Arrays.stream(numbers).max().orElse(0);
        return greatest <= 0xFF ? 1 : greatest <= 0xFFFF ? 2 : -1;
    }

    /** The Rust type the numbers of the top stage are written in. */
    String topType() {
        return numberBytes(top) == 1 ? "u8" : "u16";
    }

    /** The Rust type the numbers of the middle stage are written in. */
    String middleType() {
        return numberBytes(middle) == 1 ? "u8" : "u16";
    }
}
