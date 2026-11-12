//! The implementation held to every line of the repository's `suite` directory.

mod common;

use common::{each_line, shown};
use notation199x::*;
use std::cmp::Ordering;
use std::collections::HashSet;

#[test]
fn white_space_is_the_set_the_suite_lists() {
    let mut listed = HashSet::new();
    let mut read = false;
    each_line("suite/white-space.txt", 1, |line| {
        listed.insert(line.scalar(0));
        read = true;
        None
    });
    if !read {
        return;
    }
    let wrong: Vec<String> = (char::MIN..=char::MAX)
        .filter(|c| is_white_space(*c) != listed.contains(c))
        .map(|c| {
            format!(
                "U+{:04X} is white space: {}, and the suite lists it: {}",
                u32::from(c),
                is_white_space(c),
                listed.contains(&c)
            )
        })
        .collect();
    assert!(wrong.is_empty(), "{}", wrong.join("\n"));
}

#[test]
fn scalar_length_answers_every_line_of_the_suite() {
    each_line("suite/scalar-length.txt", 2, |line| {
        let text = line.text(0);
        let length = line.number(1);
        (scalar_count(&text) != length).then(|| {
            format!(
                "{} is {} long, not {length}",
                shown(&text),
                scalar_count(&text)
            )
        })
    });
}

#[test]
fn scalar_order_answers_every_line_of_the_suite() {
    each_line("suite/scalar-order.txt", 3, |line| {
        let a = line.text(0);
        let b = line.text(1);
        let order = match line.one_of(2, &["LESS", "EQUAL", "GREATER"]) {
            "LESS" => Ordering::Less,
            "EQUAL" => Ordering::Equal,
            _ => Ordering::Greater,
        };
        (compare(&a, &b) != order || compare(&b, &a) != order.reverse()).then(|| {
            format!(
                "{} against {} is {:?}, not {order:?}",
                shown(&a),
                shown(&b),
                compare(&a, &b)
            )
        })
    });
}

#[test]
fn case_conversion_answers_every_line_of_the_suite() {
    each_line("suite/case.txt", 5, |line| {
        let text = line.text(0);
        let lower = line.one_of(1, &["LOWER", "UPPER"]) == "LOWER";
        let bound = line.number_or_nothing(2);
        let past = line.one_of(3, &["MAPPED", "PAST"]) == "PAST";
        let mapped = if past {
            line.empty(4);
            None
        } else {
            Some(line.text(4))
        };
        let answer = match (bound, lower) {
            (None, true) => Some(lowercase(&text)),
            (None, false) => Some(uppercase(&text)),
            (Some(bound), true) => lowercase_within(&text, bound),
            (Some(bound), false) => uppercase_within(&text, bound),
        };
        if bound.is_none() && past {
            return Some("a conversion without a bound is never past one".into());
        }
        (answer != mapped).then(|| {
            format!(
                "{} is {:?}, not {:?}",
                shown(&text),
                answer.as_deref().map(shown),
                mapped.as_deref().map(shown)
            )
        })
    });
}

#[test]
fn normalization_within_a_bound_answers_every_line_of_the_suite() {
    each_line("suite/normalization-bound.txt", 5, |line| {
        let text = line.text(0);
        let form = match line.one_of(1, &["NFC", "NFD", "NFKC", "NFKD"]) {
            "NFC" => Form::Nfc,
            "NFD" => Form::Nfd,
            "NFKC" => Form::Nfkc,
            _ => Form::Nfkd,
        };
        let bound = line.number(2);
        let past = line.one_of(3, &["NORMALIZED", "PAST"]) == "PAST";
        let normalized = if past {
            line.empty(4);
            None
        } else {
            Some(line.text(4))
        };
        let answer = normalize_within(form, &text, bound);
        (answer != normalized).then(|| {
            format!(
                "{} in {form:?} within {bound} is {:?}",
                shown(&text),
                answer.as_deref().map(shown)
            )
        })
    });
}

#[test]
fn temporal_text_answers_every_line_of_the_suite() {
    each_line("suite/temporal.txt", 3, |line| {
        let kind = match line.one_of(
            0,
            &["DATE", "TIME", "DATETIME", "OFFSET_DATETIME", "INSTANT"],
        ) {
            "DATE" => TemporalKind::Date,
            "TIME" => TemporalKind::Time,
            "DATETIME" => TemporalKind::DateTime,
            "OFFSET_DATETIME" => TemporalKind::OffsetDateTime,
            _ => TemporalKind::Instant,
        };
        let text = line.text(1);
        let admitted = line.one_of(2, &["ADMITTED", "REFUSED"]) == "ADMITTED";
        let answer = check_temporal(kind, &text);
        (answer.is_ok() != admitted).then(|| format!("{} as {kind:?} is {answer:?}", shown(&text)))
    });
}

/// The readers are this crate's own, so the suite holds no value they give; on every line, the
/// reader of the kind admits what `check_temporal` admits and refuses with the same refusal.
#[test]
fn temporal_readers_admit_what_the_check_admits_on_every_line_of_the_suite() {
    each_line("suite/temporal.txt", 3, |line| {
        let kind = match line.one_of(
            0,
            &["DATE", "TIME", "DATETIME", "OFFSET_DATETIME", "INSTANT"],
        ) {
            "DATE" => TemporalKind::Date,
            "TIME" => TemporalKind::Time,
            "DATETIME" => TemporalKind::DateTime,
            "OFFSET_DATETIME" => TemporalKind::OffsetDateTime,
            _ => TemporalKind::Instant,
        };
        let text = line.text(1);
        line.one_of(2, &["ADMITTED", "REFUSED"]);
        let read = match kind {
            TemporalKind::Date => read_date(&text).map(drop),
            TemporalKind::Time => read_time(&text).map(drop),
            TemporalKind::DateTime => read_date_time(&text).map(drop),
            TemporalKind::OffsetDateTime => read_offset_date_time(&text).map(drop),
            TemporalKind::Instant => read_instant(&text).map(drop),
        };
        let checked = check_temporal(kind, &text);
        (read != checked).then(|| {
            format!(
                "{} as {kind:?} reads {read:?}, checks {checked:?}",
                shown(&text)
            )
        })
    });
}

#[test]
fn gregorian_days_answer_every_line_of_the_suite() {
    each_line("suite/gregorian.txt", 5, |line| {
        let year = line.signed_number(0);
        let month = line.signed_number(1);
        let day = line.signed_number(2);
        let is_a_day = line.one_of(3, &["DAY", "NONE"]) == "DAY";
        let said = format!("{year}-{month}-{day}");
        let (month, day) = match (u32::try_from(month), u32::try_from(day)) {
            (Ok(month), Ok(day)) => (month, day),
            _ => {
                // A month or a day below nought is no day, whatever the year.
                line.empty(4);
                return is_a_day.then(|| format!("{said} is no day, and the line says it is"));
            }
        };
        let length = length_of_month(year, month);
        if is_a_day != length.is_some_and(|length| (1..=length).contains(&day)) {
            return Some(format!(
                "{said} is a day of a month of {length:?} days: {}",
                !is_a_day
            ));
        }
        let asked = epoch_day(year, month, day);
        if !is_a_day {
            line.empty(4);
            return asked.map(|count| format!("{said} is the count {count}"));
        }
        let count = line.signed_number(4);
        if asked != Some(count) {
            return Some(format!("{said} is {asked:?}, not {count}"));
        }
        let back = day_of(count);
        (back != Some(GregorianDay { year, month, day }))
            .then(|| format!("the count {count} is {back:?}, not {said}"))
    });
}

#[test]
fn the_counts_at_the_ends_are_the_ends_of_the_years_a_date_holds() {
    assert_eq!(epoch_day(YEAR_MIN, 1, 1), Some(EPOCH_DAY_MIN));
    assert_eq!(epoch_day(YEAR_MAX, 12, 31), Some(EPOCH_DAY_MAX));
    assert_eq!(
        day_of(EPOCH_DAY_MIN),
        Some(GregorianDay {
            year: YEAR_MIN,
            month: 1,
            day: 1
        })
    );
    assert_eq!(
        day_of(EPOCH_DAY_MAX),
        Some(GregorianDay {
            year: YEAR_MAX,
            month: 12,
            day: 31
        })
    );
    for beyond in [EPOCH_DAY_MIN - 1, EPOCH_DAY_MAX + 1, i64::MIN, i64::MAX] {
        assert_eq!(day_of(beyond), None, "{beyond}");
    }
    for year in [YEAR_MIN - 1, YEAR_MAX + 1, i64::MIN, i64::MAX] {
        assert_eq!(length_of_month(year, 1), None, "{year}");
    }
    assert_eq!(length_of_month(2024, 0), None);
    assert_eq!(length_of_month(2024, 13), None);
}

/// Every count of eight hundred thousand days either side of the epoch is a day, the day after the
/// one before it, and counts back to itself; and counts drawn from the whole range do the same.
#[test]
fn every_count_is_the_day_after_the_one_before_it() {
    let mut previous = day_of(-800_000).expect("a day");
    for count in -799_999..=800_000 {
        let day = day_of(count).expect("a day");
        let expected = if day.day > 1 {
            GregorianDay {
                day: previous.day + 1,
                ..previous
            }
        } else if day.month > 1 {
            GregorianDay {
                month: previous.month + 1,
                day: 1,
                ..previous
            }
        } else {
            GregorianDay {
                year: previous.year + 1,
                month: 1,
                day: 1,
            }
        };
        assert_eq!(day, expected, "{count}");
        assert_eq!(epoch_day(day.year, day.month, day.day), Some(count));
        previous = day;
    }
    let mut state: u64 = 1970;
    for _ in 0..200_000 {
        state = state
            .wrapping_mul(6_364_136_223_846_793_005)
            .wrapping_add(1_442_695_040_888_963_407);
        let span = (EPOCH_DAY_MAX - EPOCH_DAY_MIN + 1) as u64;
        let count = EPOCH_DAY_MIN + ((state >> 11) % span) as i64;
        let day = day_of(count).expect("a day");
        assert_eq!(
            epoch_day(day.year, day.month, day.day),
            Some(count),
            "{count}"
        );
    }
}

fn limit_named(name: &str) -> PatternLimit {
    match name {
        "REPETITION_COUNT" => PatternLimit::RepetitionCount,
        "NESTING_DEPTH" => PatternLimit::NestingDepth,
        _ => PatternLimit::MachineStates,
    }
}

#[test]
fn patterns_are_read_as_every_line_of_the_suite_says() {
    each_line("suite/pattern-read.txt", 3, |line| {
        let pattern = line.text(0);
        let outcome = line.one_of(1, &["READ", "REFUSED", "BEYOND"]);
        let limit = if outcome == "BEYOND" {
            line.one_of_or_nothing(2, &["REPETITION_COUNT", "NESTING_DEPTH", "MACHINE_STATES"])
        } else {
            line.empty(2);
            None
        };
        let read = read_pattern(&pattern);
        let as_said = match &read {
            PatternRead::Pattern(_) => outcome == "READ",
            PatternRead::Refused(_) => outcome == "REFUSED",
            PatternRead::Beyond(beyond) => {
                outcome == "BEYOND" && limit.is_none_or(|limit| beyond.limit == limit_named(limit))
            }
        };
        (!as_said).then(|| format!("{} is {read:?}, not {outcome} {limit:?}", shown(&pattern)))
    });
}

/// The states the specifications state the limit in, which the file's rule is written against: not
/// the one this implementation holds, which is what is tested.
const MOST_STATES: usize = 250_000;

#[test]
fn pattern_states_are_counted_as_every_line_of_the_suite_says() {
    each_line("suite/pattern-states.txt", 2, |line| {
        let pattern = line.text(0);
        let states = line.number(1);
        let at = read_pattern(&format!(
            "(?:{pattern})|a{{0,{}}}",
            MOST_STATES - 5 - states
        ));
        let past = read_pattern(&format!(
            "(?:{pattern})|a{{0,{}}}",
            MOST_STATES - 4 - states
        ));
        if !matches!(at, PatternRead::Pattern(_)) {
            return Some(format!("{} at the limit is {at:?}", shown(&pattern)));
        }
        if !matches!(&past, PatternRead::Beyond(beyond) if beyond.limit == PatternLimit::MachineStates)
        {
            return Some(format!("{} past the limit is {past:?}", shown(&pattern)));
        }
        None
    });
}

#[test]
fn patterns_accept_what_every_line_of_the_suite_says() {
    each_line("suite/pattern-match.txt", 3, |line| {
        let pattern = line.text(0);
        let subject = line.text(1);
        let accepted = line.yes_or_no(2);
        let PatternRead::Pattern(read) = read_pattern(&pattern) else {
            return Some(format!(
                "{} is not read: {:?}",
                shown(&pattern),
                read_pattern(&pattern)
            ));
        };
        (read.matches(&subject) != accepted).then(|| {
            format!(
                "{} accepts {}: {}",
                shown(&pattern),
                shown(&subject),
                !accepted
            )
        })
    });
}

/// Owned matchers sharing one pattern answer every line as the pattern does, and answer it again
/// the same after matching the other subjects of the suite, so what one keeps from a match never
/// changes the answer of another match, or of another matcher.
#[test]
fn an_owned_matcher_answers_every_line_as_the_pattern_does() {
    let mut subjects = Vec::new();
    each_line("suite/pattern-match.txt", 3, |line| {
        let _ = (line.text(0), line.yes_or_no(2));
        subjects.push(line.text(1));
        None
    });
    each_line("suite/pattern-match.txt", 3, |line| {
        let pattern = line.text(0);
        let _ = (line.text(1), line.yes_or_no(2));
        let PatternRead::Pattern(read) = read_pattern(&pattern) else {
            return None;
        };
        let expected: Vec<bool> = subjects.iter().map(|s| read.matches(s)).collect();
        let shared = std::sync::Arc::new(read);
        let mut alone = OwnedMatcher::new(std::sync::Arc::clone(&shared));
        let mut other = OwnedMatcher::new(std::sync::Arc::clone(&shared));
        for round in 0..2 {
            for (subject, &want) in subjects.iter().zip(&expected) {
                if alone.matches(subject) != want || other.matches(subject) != want {
                    return Some(format!(
                        "{} accepts {} in round {round}: {}",
                        shown(&pattern),
                        shown(subject),
                        !want
                    ));
                }
            }
        }
        None
    });
}
