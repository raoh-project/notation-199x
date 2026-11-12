package net.unit8.notation199x;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link Gregorian} answers every line of {@code suite/gregorian.txt}: which count a year, a month
 * and a day is, which of the three a count is, and that a day is one exactly where its month has it.
 */
class GregorianAnswersEveryLineOfTheSuiteTest {

    @Test
    void everyDayIsACountOrNoneAsTheLineSays() {
        assertEquals(List.of(), Suite.wrong("gregorian.txt", 5, line -> {
            long year = line.signedNumber(0);
            int month = line.signedInt(1);
            int day = line.signedInt(2);
            boolean isADay = line.oneOf(3, "DAY", "NONE").equals("DAY");
            OptionalLong asked = Gregorian.epochDay(year, month, day);
            String said = year + "-" + month + "-" + day;
            OptionalInt length = Gregorian.lengthOfMonth(year, month);
            if (isADay != (length.isPresent() && day >= 1 && day <= length.getAsInt())) {
                return said + " is a day of a month of " + length + " days: " + !isADay;
            }
            if (!isADay) {
                line.empty(4);
                return asked.isEmpty() ? null : said + " is the count " + asked.getAsLong();
            }
            long count = line.signedNumber(4);
            if (asked.isEmpty() || asked.getAsLong() != count) {
                return said + " is " + asked + ", not " + count;
            }
            Optional<Gregorian.Day> back = Gregorian.day(count);
            return back.equals(Optional.of(new Gregorian.Day(year, month, day))) ? null
                    : "the count " + count + " is " + back + ", not " + said;
        }));
    }

    /** A text is a date exactly where its year, month and day are a day {@link Gregorian} answers. */
    @Test
    void aDateTextIsAdmittedWhereTheLineSaysItsDayExists() {
        // A month or a day below nought, or of three digits, cannot be written in a date's text, so
        // the lines that name one are not asked here; the rest are, and must reach both answers.
        int[] asked = new int[2];
        assertEquals(List.of(), Suite.wrong("gregorian.txt", 5, line -> {
            long year = line.signedNumber(0);
            long month = line.signedNumber(1);
            long day = line.signedNumber(2);
            boolean isADay = line.oneOf(3, "DAY", "NONE").equals("DAY");
            line.signedNumberOrNothing(4);
            if (month < 0 || month > 99 || day < 0 || day > 99) {
                return null;
            }
            String text = (year >= 0 && year <= 9999 ? String.format(Locale.ROOT, "%04d", year)
                    : year < 0 && year >= -9999 ? String.format(Locale.ROOT, "-%04d", -year)
                    : (year > 0 ? "+" : "") + year) + String.format(Locale.ROOT, "-%02d-%02d", month, day);
            boolean admitted = TemporalText.refusal(TemporalText.Kind.DATE, text).isEmpty();
            asked[isADay ? 0 : 1]++;
            return admitted == isADay ? null : text + " is admitted: " + admitted;
        }));
        assertTrue(asked[0] > 30 && asked[1] > 15, "the lines asked reach both answers: "
                + asked[0] + " and " + asked[1]);
    }

    @Test
    void theCountsAtTheEndsAreTheEndsOfTheYearsADateHolds() {
        assertEquals(OptionalLong.of(Gregorian.EPOCH_DAY_MIN),
                Gregorian.epochDay(TemporalText.YEAR_MIN, 1, 1));
        assertEquals(OptionalLong.of(Gregorian.EPOCH_DAY_MAX),
                Gregorian.epochDay(TemporalText.YEAR_MAX, 12, 31));
        assertEquals(Optional.of(new Gregorian.Day(TemporalText.YEAR_MIN, 1, 1)),
                Gregorian.day(Gregorian.EPOCH_DAY_MIN));
        assertEquals(Optional.of(new Gregorian.Day(TemporalText.YEAR_MAX, 12, 31)),
                Gregorian.day(Gregorian.EPOCH_DAY_MAX));
        assertEquals(Optional.empty(), Gregorian.day(Gregorian.EPOCH_DAY_MIN - 1));
        assertEquals(Optional.empty(), Gregorian.day(Gregorian.EPOCH_DAY_MAX + 1));
        assertEquals(Optional.empty(), Gregorian.day(Long.MIN_VALUE));
        assertEquals(Optional.empty(), Gregorian.day(Long.MAX_VALUE));
    }

    /** What the entry point answers for a year a date holds is what the arithmetic under it says,
     *  and the arithmetic goes on past those years where the entry point does not. */
    @Test
    void theArithmeticUnderTheEntryPointGoesOnWhereTheEntryPointStops() {
        for (long year : new long[] {TemporalText.YEAR_MIN, -400, -100, -4, 0, 4, 100, 400, 2024,
                TemporalText.YEAR_MAX}) {
            for (int month = 1; month <= 12; month++) {
                assertEquals(OptionalInt.of(Gregorian.monthLengthOf(year, month)),
                        Gregorian.lengthOfMonth(year, month), year + "-" + month);
            }
        }
        for (long year : new long[] {TemporalText.YEAR_MAX + 1, 1_000_000_000L, -1_000_000_000L}) {
            assertEquals(OptionalInt.empty(), Gregorian.lengthOfMonth(year, 2), "" + year);
            assertEquals(29, Gregorian.monthLengthOf(year, 2), "" + year);
        }
    }

    @Test
    void aDayThereIsNotCannotBeMade() {
        assertEquals(2024, new Gregorian.Day(2024, 2, 29).year());
        for (long[] each : new long[][] {{2024, 13, 40}, {2023, 2, 29}, {2024, 0, 1}, {2024, 1, 0},
                {TemporalText.YEAR_MAX + 1, 1, 1}, {TemporalText.YEAR_MIN - 1, 12, 31}}) {
            assertThrows(IllegalArgumentException.class,
                    () -> new Gregorian.Day(each[0], (int) each[1], (int) each[2]), Arrays.toString(each));
        }
    }

    @Test
    void aMonthOutsideOneToTwelveHasNoLength() {
        assertEquals(OptionalInt.empty(), Gregorian.lengthOfMonth(2024, 0));
        assertEquals(OptionalInt.empty(), Gregorian.lengthOfMonth(2024, 13));
        assertEquals(OptionalInt.empty(), Gregorian.lengthOfMonth(TemporalText.YEAR_MAX + 1, 1));
        assertEquals(OptionalInt.empty(), Gregorian.lengthOfMonth(TemporalText.YEAR_MIN - 1, 1));
    }
}
