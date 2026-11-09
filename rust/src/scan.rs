//! The scan that normalization and case conversion both begin with: going over the part of a text
//! a rule leaves as it is.

/// The ASCII characters a rule keeps.
pub(crate) struct KeptAscii {
    /// Whether the rule keeps all of ASCII, which a scan can tell of a run by its bytes alone.
    all: bool,
    table: [bool; 128],
}

impl KeptAscii {
    /// The rule that keeps all of ASCII.
    pub(crate) const ALL: KeptAscii = KeptAscii {
        all: true,
        table: [true; 128],
    };

    /// The rule that keeps the ASCII characters `table` is true of.
    pub(crate) const fn of(table: [bool; 128]) -> KeptAscii {
        KeptAscii { all: false, table }
    }

    /// Whether the rule keeps the ASCII character `byte`.
    fn keeps(&self, byte: u8) -> bool {
        byte < 0x80 && self.table[usize::from(byte)]
    }
}

/// What a scan went over: where it ended, how many code points it went past, where the last of them
/// begins, which is where the scan began where there are none, and what the rule said of the code
/// point past ASCII that it stopped at, where it stopped at one.
pub(crate) struct Scanned<T> {
    pub(crate) end: usize,
    pub(crate) count: usize,
    pub(crate) last: usize,
    pub(crate) stopper: Option<T>,
}

/// Where the code points of `text` from byte `at` that a rule keeps end: the first code point from
/// there that it does not keep, or the end of the text, or where the scan has gone past one more
/// than `left` of them.
///
/// A rule says which ASCII characters it keeps by `kept`, and the others by `stops`, which is
/// asked only of what is past ASCII and answers `Some` of what the rule has to say of a code point it
/// does not keep, so that the caller that goes on from there does not ask again. A rule that keeps all
/// of ASCII is [`KeptAscii::ALL`].
#[inline(always)]
pub(crate) fn kept_up_to<T>(
    text: &str,
    mut at: usize,
    left: usize,
    kept: &KeptAscii,
    stops: impl Fn(char) -> Option<T>,
) -> Scanned<T> {
    let mut count = 0;
    let mut last = at;
    // How many bytes of ASCII in a row the rule has kept, which is as many code points.
    let mut streak = 0;
    while at < text.len() && count <= left {
        let bytes = &text.as_bytes()[..scan_stop(text, at, left, count)];
        while at < bytes.len() {
            if bytes[at] < 0x80 {
                if !kept.keeps(bytes[at]) {
                    return Scanned {
                        end: at,
                        count,
                        last,
                        stopper: None,
                    };
                }
                last = at;
                at += 1;
                count += 1;
                streak += 1;
                if streak == LONG_RUN {
                    // Most runs of ASCII in text are short, between code points past it, and are gone
                    // over a byte at a time; one that is longer is gone over eight at a time.
                    let more = long_ascii_run(&bytes[at..], kept);
                    at += more;
                    count += more;
                    if more > 0 {
                        last = at - 1;
                    }
                    streak = 0;
                }
                continue;
            }
            streak = 0;
            let c = text[at..]
                .chars()
                .next()
                .expect("a byte that is not ASCII begins a character here");
            if let Some(stopper) = stops(c) {
                return Scanned {
                    end: at,
                    count,
                    last,
                    stopper: Some(stopper),
                };
            }
            last = at;
            at += c.len_utf8();
            count += 1;
        }
    }
    Scanned {
        end: at,
        count,
        last,
        stopper: None,
    }
}

/// How far a scan of `text` from byte `at`, which may go past one more than `left` code points and
/// has gone past `count`, can go without counting them: a code point is a byte or more, so up to
/// there it goes past no more than that, and a code point past ASCII can leave it short, where the
/// scan goes on from there. What is left is held against the bytes left before anything is added
/// to `at`, so that no bound, however large, takes it past the end.
pub(crate) fn scan_stop(text: &str, at: usize, left: usize, count: usize) -> usize {
    if left - count >= text.len() - at {
        text.len()
    } else {
        at + left - count + 1
    }
}

/// How many ASCII bytes in a row a scan has gone over before it goes over the rest of them eight at a
/// time.
const LONG_RUN: usize = 8;

/// How many bytes `bytes` begins with that are ASCII characters `kept` keeps, eight at a time
/// while eight are left and the rest one at a time. Not inlined: the loop of the scan is the same
/// for a run of ASCII that is short as for text with none, and what it does with a long one is
/// kept out of it.
#[inline(never)]
fn long_ascii_run(bytes: &[u8], kept: &KeptAscii) -> usize {
    let mut at = 0;
    for chunk in bytes.as_chunks::<8>().0 {
        let past_ascii = u64::from_le_bytes(*chunk) & 0x8080_8080_8080_8080 != 0;
        if past_ascii || !kept.all && !chunk.iter().all(|&byte| kept.keeps(byte)) {
            break;
        }
        at += 8;
    }
    at + bytes[at..]
        .iter()
        .position(|&byte| !kept.keeps(byte))
        .unwrap_or(bytes.len() - at)
}

#[cfg(test)]
mod tests {
    use super::*;
    use alloc::vec::Vec;

    fn only(kept: &[u8]) -> KeptAscii {
        KeptAscii::of(core::array::from_fn(|byte| kept.contains(&(byte as u8))))
    }

    fn without(dropped: u8) -> KeptAscii {
        KeptAscii::of(core::array::from_fn(|byte| byte != usize::from(dropped)))
    }

    /// A run of ASCII ends at the first byte that is not kept, wherever it comes and however long
    /// the run is.
    #[test]
    fn a_long_ascii_run_ends_at_the_first_byte_not_kept() {
        let mut seed = 7u64;
        let mut next = move || {
            seed = seed.wrapping_mul(6_364_136_223_846_793_005).wrapping_add(1);
            seed >> 33
        };
        for kept in [KeptAscii::ALL, only(b""), only(b"ab"), without(b'z')] {
            for length in 0..40 {
                for _ in 0..200 {
                    // Mostly kept, so that runs are long and every place gets a first byte past it.
                    let bytes: Vec<u8> = (0..length)
                        .map(|_| match next() % 9 {
                            0 => 0x80 | next() as u8,
                            1 => b'z',
                            _ => b"ab"[next() as usize % 2],
                        })
                        .collect();
                    let expected = bytes
                        .iter()
                        .position(|&b| !kept.keeps(b))
                        .unwrap_or(bytes.len());
                    assert_eq!(long_ascii_run(&bytes, &kept), expected, "{bytes:?}");
                }
            }
        }
    }
}
