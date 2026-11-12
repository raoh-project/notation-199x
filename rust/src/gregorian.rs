//! The days of the proleptic Gregorian calendar, counted from 1970-01-01.
//!
//! A caller that holds a day as a count, as a run time with no date library does, asks here which
//! count a year, a month and a day is and which year, month and day a count is, and so answers as
//! every other caller of this crate does.
//!
//! The domain is that of a date: the years from [`YEAR_MIN`](crate::YEAR_MIN) to
//! [`YEAR_MAX`](crate::YEAR_MAX), and the counts from [`EPOCH_DAY_MIN`] to [`EPOCH_DAY_MAX`] that
//! are the days of those years. A question about a day outside it is answered that there is none,
//! so a caller that moves a day by a number of months asks where it landed and is told whether it
//! is a day.

use crate::temporal::{YEAR_MAX, YEAR_MIN};

/// A day: its year, its month from 1 to 12, and its day of the month from 1.
#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash)]
pub struct GregorianDay {
    /// The year, astronomical: the year before 1 is 0.
    pub year: i64,
    /// The month, from 1 to 12.
    pub month: u32,
    /// The day of the month, from 1.
    pub day: u32,
}

/// The count of the first day of [`YEAR_MIN`](crate::YEAR_MIN).
pub const EPOCH_DAY_MIN: i64 = -365_243_219_162;
/// The count of the last day of [`YEAR_MAX`](crate::YEAR_MAX).
pub const EPOCH_DAY_MAX: i64 = 365_241_780_471;

/// Days from 0000-03-01 to 1970-01-01, which the counts below are taken from.
const DAYS_TO_EPOCH: i64 = 719_468;

/// Whether `year` has a 29 February.
pub fn is_leap_year(year: i64) -> bool {
    year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)
}

/// How many days a month of a year has, or nothing where the year is not one a date holds or the
/// month is not from 1 to 12.
pub fn length_of_month(year: i64, month: u32) -> Option<u32> {
    if !(YEAR_MIN..=YEAR_MAX).contains(&year) {
        return None;
    }
    match month {
        2 if is_leap_year(year) => Some(29),
        2 => Some(28),
        4 | 6 | 9 | 11 => Some(30),
        1 | 3 | 5 | 7 | 8 | 10 | 12 => Some(31),
        _ => None,
    }
}

/// The count of a day: how many days it is from 1970-01-01, negative before it. Nothing where the
/// three do not name a day of a year a date holds.
pub fn epoch_day(year: i64, month: u32, day: u32) -> Option<i64> {
    let length = length_of_month(year, month)?;
    (1..=length)
        .contains(&day)
        .then(|| count_of(year, i64::from(month), i64::from(day)))
}

/// The day a count is, or nothing where the count is not that of a day of a year a date holds.
pub fn day_of(epoch_day: i64) -> Option<GregorianDay> {
    if !(EPOCH_DAY_MIN..=EPOCH_DAY_MAX).contains(&epoch_day) {
        return None;
    }
    let z = epoch_day + DAYS_TO_EPOCH;
    let era = z.div_euclid(146_097);
    let day_of_era = z - era * 146_097;
    let year_of_era =
        (day_of_era - day_of_era / 1460 + day_of_era / 36_524 - day_of_era / 146_096) / 365;
    let day_of_year = day_of_era - (365 * year_of_era + year_of_era / 4 - year_of_era / 100);
    let from_march = (5 * day_of_year + 2) / 153;
    let day = (day_of_year - (153 * from_march + 2) / 5 + 1) as u32;
    let month = if from_march < 10 {
        from_march + 3
    } else {
        from_march - 9
    } as u32;
    let year = year_of_era + era * 400 + i64::from(month <= 2);
    Some(GregorianDay { year, month, day })
}

/// The count of a day the caller has established exists. The year runs from March, so the leap day
/// is the last of it.
pub(crate) fn count_of(year: i64, month: i64, day: i64) -> i64 {
    let shifted = if month <= 2 { year - 1 } else { year };
    let era = shifted.div_euclid(400);
    let year_of_era = shifted - era * 400;
    let day_of_year = (153 * (if month > 2 { month - 3 } else { month + 9 }) + 2) / 5 + day - 1;
    let day_of_era = year_of_era * 365 + year_of_era / 4 - year_of_era / 100 + day_of_year;
    era * 146_097 + day_of_era - DAYS_TO_EPOCH
}
