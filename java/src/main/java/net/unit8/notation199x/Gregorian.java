package net.unit8.notation199x;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;

/**
 * The days of the proleptic Gregorian calendar, counted from 1970-01-01.
 *
 * <p>A caller that holds a day as a count, as a run time with no date library does, asks here which
 * count a year, a month and a day is and which year, month and day a count is, and so answers as
 * every other caller of this library does. Nothing here asks {@code java.time}.
 *
 * <p>The domain is that of a date: the years from {@link TemporalText#YEAR_MIN} to
 * {@link TemporalText#YEAR_MAX}, and the counts from {@link #EPOCH_DAY_MIN} to
 * {@link #EPOCH_DAY_MAX} that are the days of those years. A question about a day outside it is
 * answered that there is none, so a caller that moves a day by a number of months asks where it
 * landed and is told whether it is a day.
 */
public final class Gregorian {

    /** A day: its year, its month from 1 to 12, and its day of the month from 1. */
    public record Day(long year, int month, int day) {}

    /** The count of the first day of {@link TemporalText#YEAR_MIN}. */
    public static final long EPOCH_DAY_MIN = -365_243_219_162L;

    /** The count of the last day of {@link TemporalText#YEAR_MAX}. */
    public static final long EPOCH_DAY_MAX = 365_241_780_471L;

    /** Days from 0000-03-01 to 1970-01-01, which the counts below are taken from. */
    private static final long DAYS_TO_EPOCH = 719_468L;

    private Gregorian() {}

    /**
     * Whether {@code year} has a 29 February.
     *
     * @param year the year, astronomical: the year before 1 is 0
     * @return whether it is a leap year
     */
    public static boolean isLeapYear(long year) {
        return (year & 3) == 0 && (year % 100 != 0 || year % 400 == 0);
    }

    /**
     * How many days a month of a year has, or empty where the year is not one a date holds or the
     * month is not from 1 to 12.
     *
     * @param year the year
     * @param month the month, from 1 to 12
     * @return the days of the month
     */
    public static OptionalInt lengthOfMonth(long year, int month) {
        if (!inRange(year) || month < 1 || month > 12) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(switch (month) {
            case 2 -> isLeapYear(year) ? 29 : 28;
            case 4, 6, 9, 11 -> 30;
            default -> 31;
        });
    }

    /**
     * The count of a day: how many days it is from 1970-01-01, negative before it. Empty where
     * the three do not name a day of a year a date holds.
     *
     * @param year the year
     * @param month the month, from 1 to 12
     * @param day the day of the month, from 1
     * @return the count
     */
    public static OptionalLong epochDay(long year, int month, int day) {
        OptionalInt length = lengthOfMonth(year, month);
        if (length.isEmpty() || day < 1 || day > length.getAsInt()) {
            return OptionalLong.empty();
        }
        return OptionalLong.of(countOf(year, month, day));
    }

    /**
     * The day a count is, or empty where the count is not that of a day of a year a date holds.
     *
     * @param epochDay how many days from 1970-01-01
     * @return the day
     */
    public static Optional<Day> day(long epochDay) {
        if (epochDay < EPOCH_DAY_MIN || epochDay > EPOCH_DAY_MAX) {
            return Optional.empty();
        }
        long z = epochDay + DAYS_TO_EPOCH;
        long era = Math.floorDiv(z, 146_097L);
        long dayOfEra = z - era * 146_097L;
        long yearOfEra = (dayOfEra - dayOfEra / 1460 + dayOfEra / 36_524 - dayOfEra / 146_096) / 365;
        long dayOfYear = dayOfEra - (365 * yearOfEra + yearOfEra / 4 - yearOfEra / 100);
        long fromMarch = (5 * dayOfYear + 2) / 153;
        int day = (int) (dayOfYear - (153 * fromMarch + 2) / 5 + 1);
        int month = (int) (fromMarch < 10 ? fromMarch + 3 : fromMarch - 9);
        long year = yearOfEra + era * 400 + (month <= 2 ? 1 : 0);
        return Optional.of(new Day(year, month, day));
    }

    private static boolean inRange(long year) {
        return year >= TemporalText.YEAR_MIN && year <= TemporalText.YEAR_MAX;
    }

    /** The count of a day the caller has established exists. The year runs from March, so the leap
     *  day is the last of it. */
    static long countOf(long year, int month, int day) {
        long shifted = month <= 2 ? year - 1 : year;
        long era = Math.floorDiv(shifted, 400L);
        long yearOfEra = shifted - era * 400L;
        long dayOfYear = (153L * (month > 2 ? month - 3 : month + 9) + 2) / 5 + day - 1;
        long dayOfEra = yearOfEra * 365 + yearOfEra / 4 - yearOfEra / 100 + dayOfYear;
        return era * 146_097L + dayOfEra - DAYS_TO_EPOCH;
    }
}
