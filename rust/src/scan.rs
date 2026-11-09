//! The scan that normalization and case conversion both begin with: going over the part of a text
//! a rule leaves as it is.

/// Where the code points of `text` from byte `at` that a rule keeps end: the first code point from
/// there that it does not keep, or the end of the text, or where the scan has gone past one more
/// than `left` of them; how many it went past; and where the last of them begins, which is `at`
/// where there are none.
///
/// A rule says which ASCII characters it keeps by `kept_ascii`, a bit for each, and the others by
/// `keeps`, which is asked only of what is past ASCII. A rule that keeps all of ASCII passes
/// `u128::MAX`, and the scan goes over its ASCII eight bytes at a time.
#[inline(always)]
pub(crate) fn kept_up_to(
    text: &str,
    mut at: usize,
    left: usize,
    kept_ascii: u128,
    keeps: impl Fn(char) -> bool,
) -> (usize, usize, usize) {
    let mut count = 0;
    let mut last = at;
    while at < text.len() && count <= left {
        let bytes = &text.as_bytes()[..scan_stop(text, at, left, count)];
        while at < bytes.len() {
            let run = ascii_run(&bytes[at..], kept_ascii);
            if run > 0 {
                at += run;
                count += run;
                last = at - 1;
                if at == bytes.len() {
                    break;
                }
            }
            if bytes[at] < 0x80 {
                return (at, count, last);
            }
            let c = text[at..]
                .chars()
                .next()
                .expect("a byte that is not ASCII begins a character here");
            if !keeps(c) {
                return (at, count, last);
            }
            last = at;
            at += c.len_utf8();
            count += 1;
        }
    }
    (at, count, last)
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

/// How many bytes `bytes` begins with that are ASCII characters `kept_ascii` has a bit for, which
/// are as many code points: eight at a time while eight are left, and the rest one at a time.
#[inline(always)]
fn ascii_run(bytes: &[u8], kept_ascii: u128) -> usize {
    let kept = |byte: u8| byte < 0x80 && kept_ascii >> byte & 1 != 0;
    let mut at = 0;
    for chunk in bytes.as_chunks::<8>().0 {
        // A word with no byte past ASCII is all kept where the rule keeps all of ASCII.
        let past_ascii = u64::from_le_bytes(*chunk) & 0x8080_8080_8080_8080 != 0;
        if past_ascii || kept_ascii != u128::MAX && !chunk.iter().all(|&byte| kept(byte)) {
            break;
        }
        at += 8;
    }
    at + bytes[at..]
        .iter()
        .position(|&byte| !kept(byte))
        .unwrap_or(bytes.len() - at)
}

#[cfg(test)]
mod tests {
    use super::*;
    use alloc::vec::Vec;

    /// A run of ASCII ends at the first byte that is not kept, wherever it comes and however long
    /// the run is.
    #[test]
    fn an_ascii_run_ends_at_the_first_byte_not_kept() {
        let mut seed = 7u64;
        let mut next = move || {
            seed = seed.wrapping_mul(6_364_136_223_846_793_005).wrapping_add(1);
            seed >> 33
        };
        for kept_ascii in [u128::MAX, 0, 1 << b'a' | 1 << b'b', !(1 << b'z')] {
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
                        .position(|&b| b >= 0x80 || kept_ascii >> b & 1 == 0)
                        .unwrap_or(bytes.len());
                    assert_eq!(ascii_run(&bytes, kept_ascii), expected, "{bytes:?}");
                }
            }
        }
    }
}
