package net.unit8.notation199x;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What {@link Gregorian} counts is what {@code java.time} counts. The calendar is arithmetic here
 * and {@code java.time} is the oracle: every day of four hundred years and so of every pattern the
 * leap rule makes, then days drawn from the whole range, and the ends of it.
 */
class GregorianAgreesWithJavaTimeOnEveryDayTest {

    @Test
    void everyDayOfFourCenturiesIsTheCountJavaTimeGivesIt() {
        for (long count = LocalDate.of(1600, 1, 1).toEpochDay();
                count <= LocalDate.of(2000, 12, 31).toEpochDay(); count++) {
            assertSame(LocalDate.ofEpochDay(count));
        }
    }

    @Test
    void daysDrawnFromTheWholeRangeAreTheCountsJavaTimeGivesThem() {
        Random random = new Random(1970);
        for (int i = 0; i < 500_000; i++) {
            assertSame(LocalDate.ofEpochDay(
                    random.nextLong(LocalDate.MIN.toEpochDay(), LocalDate.MAX.toEpochDay() + 1)));
        }
    }

    @Test
    void theEndsOfTheRangeAreTheEndsJavaTimeHas() {
        assertEquals(LocalDate.MIN.toEpochDay(), Gregorian.EPOCH_DAY_MIN);
        assertEquals(LocalDate.MAX.toEpochDay(), Gregorian.EPOCH_DAY_MAX);
        assertSame(LocalDate.MIN);
        assertSame(LocalDate.MAX);
    }

    @Test
    void aMonthIsAsLongAsJavaTimeSaysAtEveryEdgeOfTheYearsAndInEveryLeapPattern() {
        long[] edges = {-999_999_999, -999_999_996, -1000, -401, -400, -101, -100, -5, -4, -1, 0, 1,
                4, 100, 400, 1900, 2000, 2024, 2100, 999_999_996, 999_999_998, 999_999_999};
        for (long year : edges) {
            for (int month = 1; month <= 12; month++) {
                assertEquals(OptionalInt.of(YearMonth.of((int) year, month).lengthOfMonth()),
                        Gregorian.lengthOfMonth(year, month), year + "-" + month);
            }
        }
    }

    private static void assertSame(LocalDate date) {
        long count = date.toEpochDay();
        String said = date.toString();
        assertEquals(OptionalLong.of(count),
                Gregorian.epochDay(date.getYear(), date.getMonthValue(), date.getDayOfMonth()), said);
        assertEquals(Optional.of(new Gregorian.Day(date.getYear(), date.getMonthValue(),
                date.getDayOfMonth())), Gregorian.day(count), said);
    }
}
