use alloc::string::{String, ToString};

use crate::case_tables::{
    CASE_CONTEXT, FINAL_SIGMA, LOWER, LOWER_ASCII, LOWER_POSITION, UPPER, UPPER_ASCII,
    UPPER_POSITION,
};
use crate::scan::{KeptAscii, Scanned, kept_up_to};
use crate::tables::mapped;

/// `text` in lowercase: Unicode 18.0.0's untailored full mapping, from `UnicodeData.txt` and
/// `SpecialCasing.txt`, with no locale or language tailoring. One code point can map to several. A
/// Greek capital sigma becomes the final form only at the end of a cased run, which is the one
/// condition the default mapping carries that is context rather than locale: `lowercase("ΟΣ")` is
/// `"ος"` and `lowercase("ΟΣΑ")` is `"οσα"`.
///
/// Not [`str::to_lowercase`], which maps by the Unicode version of the Rust release.
///
/// A case mapping is not closed under any normalization form, so a caller that holds its text in
/// one normalizes the answer.
pub fn lowercase(text: &str) -> String {
    map_case::<true>(text, usize::MAX).expect("no text is longer than usize::MAX scalar values")
}

/// `text` in uppercase, by the same untailored full mapping as [`lowercase`]: one code point can
/// widen to several, so `uppercase("straße")` is `"STRASSE"`, and no locale narrows it, so a
/// Turkish `i` still becomes `I`.
pub fn uppercase(text: &str) -> String {
    map_case::<false>(text, usize::MAX).expect("no text is longer than usize::MAX scalar values")
}

/// [`lowercase`] where that is no longer than `longest` scalar values, and `None` where it is
/// longer, which is found out before more than `longest` is written. How much of `text` it reads
/// turns on `longest` and not on the length of `text`: it reads no further once what it has read
/// shows the answer to be longer.
pub fn lowercase_within(text: &str, longest: usize) -> Option<String> {
    map_case::<true>(text, longest)
}

/// [`uppercase`] where that is no longer than `longest` scalar values, and `None` where it is
/// longer, which is found out before more than `longest` is written. How much of `text` it reads
/// turns on `longest` and not on the length of `text`: it reads no further once what it has read
/// shows the answer to be longer.
pub fn uppercase_within(text: &str, longest: usize) -> Option<String> {
    map_case::<false>(text, longest)
}

/// The mapped text, or `None` where it is longer than `longest`.
///
/// Text that maps to itself is answered as it is. Otherwise what maps to itself is copied a run at
/// a time, from `kept`, and the answer is made only once a code point that changes is met. Whether
/// one does is read off [`LOWER_POSITION`] or [`UPPER_POSITION`], which answer where its mapping is as
/// well, and for ASCII off [`LOWER_ASCII`] or [`UPPER_ASCII`].
///
/// The direction is `LOWER`, fixed when the program is compiled, so that a program that maps one way
/// does not carry the tables of the other.
///
/// `Final_Sigma` holds of a sigma with a `Cased` code point before it and none after it, each looked
/// for past the `Case_Ignorable` code points between, in the text, by [`is_final_sigma`]. A sigma is
/// not `Case_Ignorable`, so what one sigma looks past another does not, and the looking around all
/// the sigmas of a text goes over each code point at most twice.
///
/// The text is gone over a code point at a time in [`kept_up_to`] and in this loop, each code point
/// by one of them, and around a sigma in [`is_final_sigma`]; no other loop turns on the text.
///
/// What is bounded is what is written: each code point's mapping is counted before any of it is
/// written, so the answer never holds more than `longest`, nor part of a mapping that would take it
/// past. Every code point maps to at least one, which the generator checks, so the text is read no
/// further than one code point past `longest` either: what maps to itself is gone past up to there,
/// and [`is_final_sigma`] looks past no more `Case_Ignorable` code points after a sigma than the
/// answer has room for.
fn map_case<const LOWER_CASE: bool>(text: &str, longest: usize) -> Option<String> {
    let position = |c: char| {
        if LOWER_CASE {
            LOWER_POSITION.get(c)
        } else {
            UPPER_POSITION.get(c)
        }
    };
    let mappings: &[(char, &[char])] = if LOWER_CASE { &LOWER } else { &UPPER };
    let ascii = if LOWER_CASE {
        &LOWER_ASCII
    } else {
        &UPPER_ASCII
    };
    let unchanged = if LOWER_CASE {
        &LOWER_UNCHANGED
    } else {
        &UPPER_UNCHANGED
    };
    let bytes = text.as_bytes();
    // The answer up to `kept`, where a code point that changes has been met.
    let mut out: Option<String> = None;
    let mut kept = 0;
    let mut written: usize = 0;
    let mut at = 0;
    while at < bytes.len() {
        // What is written is never more than `longest`, so this does not wrap.
        let Scanned {
            end: same,
            count,
            stopper,
            ..
        } = kept_up_to(text, at, longest - written, unchanged, |c| {
            Some(position(c)).filter(|&at| at != 0)
        });
        written += count;
        if written > longest {
            return None;
        }
        at = same;
        if at == bytes.len() {
            break;
        }
        let out = out.get_or_insert_with(|| String::with_capacity(text.len().min(longest)));
        out.push_str(&text[kept..at]);
        if bytes[at] < 0x80 && ascii[usize::from(bytes[at])] >= 0 {
            // The ASCII from here that changes is written as it is mapped, a character at a time.
            loop {
                if written == longest {
                    return None;
                }
                written += 1;
                out.push(char::from(ascii[usize::from(bytes[at])] as u8));
                at += 1;
                if at == bytes.len()
                    || bytes[at] >= 0x80
                    || ascii[usize::from(bytes[at])] < 0
                    || ascii[usize::from(bytes[at])] == i16::from(bytes[at])
                {
                    break;
                }
            }
            kept = at;
            continue;
        }
        let c = text[at..]
            .chars()
            .next()
            .expect("kept_up_to stops where a character begins");
        let after = at + c.len_utf8();
        // Where the scan stopped at a code point past ASCII it has asked the position already.
        let place = stopper.unwrap_or_else(|| position(c));
        let mut mapping = mappings[usize::from(place) - 1].1;
        if LOWER_CASE && has_context(c, FINAL_SIGMA_NAMED) {
            // The sigma is at least one scalar value of the answer, as every code point is, and
            // each `Case_Ignorable` one after it is another.
            if written == longest {
                return None;
            }
            if is_final_sigma(text, at, after, longest - written - 1)? {
                mapping = mapped(&FINAL_SIGMA, c).expect("CASE_CONTEXT says FINAL_SIGMA names it");
            }
        }
        if mapping.len() > longest - written {
            return None;
        }
        written += mapping.len();
        out.extend(mapping);
        kept = after;
        at = after;
    }
    Some(match out {
        None => text.to_string(),
        Some(mut out) => {
            out.push_str(&text[kept..]);
            out
        }
    })
}

/// The ASCII characters each mapping leaves as they are.
const LOWER_UNCHANGED: KeptAscii = KeptAscii::of(unchanged(&LOWER_ASCII));
const UPPER_UNCHANGED: KeptAscii = KeptAscii::of(unchanged(&UPPER_ASCII));

const fn unchanged(ascii: &[i16; 128]) -> [bool; 128] {
    let mut kept = [false; 128];
    let mut byte = 0;
    while byte < 128 {
        kept[byte] = ascii[byte] == byte as i16;
        byte += 1;
    }
    kept
}

/// Unicode's `Final_Sigma` condition of the code point between bytes `at` and `after`: preceded,
/// skipping `Case_Ignorable` code points, by a `Cased` one, and not followed, skipping the same way,
/// by another `Cased` one. Looked for as far as the text goes rather than over a window, since what
/// is skipped is decided by the property and not by a count. `None` where more than `most`
/// `Case_Ignorable` code points follow it, which leaves it undecided.
fn is_final_sigma(text: &str, at: usize, after: usize, most: usize) -> Option<bool> {
    let before = text[..at]
        .chars()
        .rev()
        .find(|&c| !has_context(c, CASE_IGNORABLE));
    if !before.is_some_and(|c| has_context(c, CASED)) {
        return Some(false);
    }
    // Every code point before the one that decides it is skipped, so `skipped` counts those.
    for (skipped, c) in text[after..].chars().enumerate() {
        if !has_context(c, CASE_IGNORABLE) {
            return Some(!has_context(c, CASED));
        }
        if skipped == most {
            return None;
        }
    }
    Some(true)
}

// What [`CASE_CONTEXT`] holds of a code point, a bit each.
const CASED: u8 = 1;
const CASE_IGNORABLE: u8 = 2;
const FINAL_SIGMA_NAMED: u8 = 4;

/// Whether [`CASE_CONTEXT`] holds `bit` of `c`.
fn has_context(c: char, bit: u8) -> bool {
    CASE_CONTEXT.get(c) & bit != 0
}

#[cfg(test)]
mod tests {
    extern crate std;

    use super::*;
    use crate::tables::ucd::{property, ucd};
    use alloc::vec::Vec;

    /// `Final_Sigma` as Java decides it: from each sigma, look back and then forward past the
    /// `Case_Ignorable` code points.
    fn final_sigma_by_looking_around(text: &[char], at: usize) -> bool {
        let before = text[..at]
            .iter()
            .rev()
            .find(|c| !has_context(**c, CASE_IGNORABLE));
        let after = text[at + 1..]
            .iter()
            .find(|c| !has_context(**c, CASE_IGNORABLE));
        before.is_some_and(|c| has_context(*c, CASED))
            && !after.is_some_and(|c| has_context(*c, CASED))
    }

    fn lowercase_by_looking_around(text: &[char]) -> String {
        let mut out = String::new();
        for (at, c) in text.iter().enumerate() {
            let final_form =
                mapped(&FINAL_SIGMA, *c).filter(|_| final_sigma_by_looking_around(text, at));
            match final_form.or_else(|| mapped(&LOWER, *c)) {
                Some(mapping) => out.extend(mapping),
                None => out.push(*c),
            }
        }
        out
    }

    /// Every text of up to six code points over a cased letter, a sigma, a `Case_Ignorable` code
    /// point, one that is both `Cased` and `Case_Ignorable`, one that is neither, and one that maps
    /// to two, lowercased and by mapping each code point by the searched tables and looking around
    /// each sigma, alike and within every bound.
    /// Each position table answers 0 where the searched mapping has nothing for a code point or
    /// maps it to itself, and otherwise one more than where the searched mapping holds it; and no
    /// code point `FINAL_SIGMA` names is 0.
    #[test]
    fn a_position_table_answers_where_the_mapping_holds_what_changes() {
        let lower = |c| LOWER_POSITION.get(c);
        let upper = |c| UPPER_POSITION.get(c);
        for (position, mapping) in [
            (&lower as &dyn Fn(char) -> u16, &LOWER[..]),
            (&upper, &UPPER[..]),
        ] {
            for c in (0..0x110000).filter_map(char::from_u32) {
                let index = mapping.binary_search_by_key(&c, |&(from, _)| from);
                let expected = match index {
                    Ok(at) if mapping[at].1 != [c] => at + 1,
                    _ => 0,
                };
                assert_eq!(usize::from(position(c)), expected, "{c:?}");
            }
        }
        for &(c, _) in &FINAL_SIGMA {
            assert_ne!(LOWER_POSITION.get(c), 0, "{c:?}");
        }
    }

    #[test]
    fn lowercase_is_the_mapping_with_final_sigma_looked_for_around_each_sigma() {
        let alphabet = ['A', 'Σ', '.', '\u{02B0}', ' ', '\u{0130}'];
        assert!(has_context('\u{02B0}', CASED) && has_context('\u{02B0}', CASE_IGNORABLE));
        assert!(has_context('.', CASE_IGNORABLE) && !has_context('.', CASED));
        let mut text: Vec<char> = Vec::new();
        for length in 0..=6u32 {
            for mut n in 0..alphabet.len().pow(length) {
                text.clear();
                for _ in 0..length {
                    text.push(alphabet[n % alphabet.len()]);
                    n /= alphabet.len();
                }
                let s: String = text.iter().collect();
                let expected = lowercase_by_looking_around(&text);
                assert_eq!(lowercase(&s), expected, "{s:?}");
                let length = expected.chars().count();
                for bound in 0..=length + 1 {
                    let within_bound = lowercase_within(&s, bound);
                    assert_eq!(
                        within_bound.is_some(),
                        bound >= length,
                        "{s:?} within {bound}"
                    );
                    if let Some(answer) = within_bound {
                        assert_eq!(answer, expected);
                    }
                }
            }
        }
    }

    /// The case context of every code point is what `DerivedCoreProperties.txt` states of `Cased`
    /// and `Case_Ignorable`, and whether [`FINAL_SIGMA`] names it.
    #[test]
    fn the_case_context_is_what_the_database_states() {
        let Some(properties) = ucd("DerivedCoreProperties.txt") else {
            return;
        };
        let cased = property(&properties, "Cased");
        let ignorable = property(&properties, "Case_Ignorable");
        for c in (0..0x110000).filter_map(char::from_u32) {
            assert_eq!(has_context(c, CASED), cased[c as usize], "{c:?}");
            assert_eq!(
                has_context(c, CASE_IGNORABLE),
                ignorable[c as usize],
                "{c:?}"
            );
            assert_eq!(
                has_context(c, FINAL_SIGMA_NAMED),
                mapped(&FINAL_SIGMA, c).is_some(),
                "{c:?}"
            );
        }
    }
}
