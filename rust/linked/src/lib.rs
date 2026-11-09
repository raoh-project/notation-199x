//! What one program asks of the crate, by feature: every call of the crate that the feature stands
//! for, so that what the program carries of the crate is what the whole of that part of its API
//! reaches. See `check.sh`.

#[cfg(any(feature = "nfc", feature = "nfd", feature = "nfkc", feature = "nfkd"))]
use notation199x::Normalizer;

/// The calls of one form fixed at compile time.
#[cfg(any(feature = "nfc", feature = "nfd", feature = "nfkc", feature = "nfkd"))]
fn every_call_of<const COMPAT: bool, const COMPOSE: bool>(text: &str, out: &mut String) {
    out.push_str(&Normalizer::<COMPAT, COMPOSE>::normalize(text));
    out.push_str(&Normalizer::<COMPAT, COMPOSE>::normalize_within(text, 100).unwrap_or_default());
    Normalizer::<COMPAT, COMPOSE>::append(out, text);
}

/// What the program of the enabled features does with `text`.
pub fn run(text: &str) -> String {
    let mut out = String::new();
    #[cfg(feature = "nfc")]
    every_call_of::<false, true>(text, &mut out);
    #[cfg(feature = "nfd")]
    every_call_of::<false, false>(text, &mut out);
    #[cfg(feature = "nfkc")]
    every_call_of::<true, true>(text, &mut out);
    #[cfg(feature = "nfkd")]
    every_call_of::<true, false>(text, &mut out);
    #[cfg(feature = "lowercase")]
    {
        out.push_str(&notation199x::lowercase(text));
        out.push_str(&notation199x::lowercase_within(text, 100).unwrap_or_default());
    }
    #[cfg(feature = "uppercase")]
    {
        out.push_str(&notation199x::uppercase(text));
        out.push_str(&notation199x::uppercase_within(text, 100).unwrap_or_default());
    }
    #[cfg(feature = "form")]
    {
        use notation199x::{Form, append_normalized, normalize, normalize_within};
        let form = match text.len() % 4 {
            0 => Form::Nfc,
            1 => Form::Nfd,
            2 => Form::Nfkc,
            _ => Form::Nfkd,
        };
        out.push_str(&normalize(form, text));
        out.push_str(&normalize_within(form, text, 100).unwrap_or_default());
        append_normalized(form, &mut out, text);
    }
    #[cfg(not(any(
        feature = "nfc",
        feature = "nfd",
        feature = "nfkc",
        feature = "nfkd",
        feature = "lowercase",
        feature = "uppercase",
        feature = "form"
    )))]
    out.push_str(text);
    out
}

/// What a WebAssembly module of the program exports, as the one thing it does.
///
/// # Safety
///
/// `ptr` points to `len` bytes of UTF-8.
#[cfg(target_arch = "wasm32")]
#[unsafe(no_mangle)]
pub unsafe extern "C" fn probe(ptr: *const u8, len: usize) -> usize {
    // SAFETY: the caller holds `ptr` to `len` bytes of UTF-8.
    run(unsafe { core::str::from_utf8_unchecked(core::slice::from_raw_parts(ptr, len)) }).len()
}
