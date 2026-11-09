//! What one program asks of the crate, by feature. See `check.sh`.

fn main() {
    let text = std::env::args().nth(1).unwrap_or_default();
    #[cfg(feature = "nfc")]
    println!("{}", notation199x::Nfc::normalize(&text));
    #[cfg(feature = "nfd")]
    println!("{}", notation199x::Nfd::normalize(&text));
    #[cfg(feature = "nfkc")]
    println!("{}", notation199x::Nfkc::normalize(&text));
    #[cfg(feature = "nfkd")]
    println!("{}", notation199x::Nfkd::normalize(&text));
    #[cfg(feature = "form")]
    {
        let form = match text.len() % 4 {
            0 => notation199x::Form::Nfc,
            1 => notation199x::Form::Nfd,
            2 => notation199x::Form::Nfkc,
            _ => notation199x::Form::Nfkd,
        };
        println!("{}", notation199x::normalize(form, &text));
    }
    #[cfg(not(any(
        feature = "nfc",
        feature = "nfd",
        feature = "nfkc",
        feature = "nfkd",
        feature = "form"
    )))]
    println!("{text}");
}
