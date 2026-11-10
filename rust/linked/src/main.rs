fn main() {
    println!(
        "{}",
        linked::run(&std::env::args().nth(1).unwrap_or_default())
    );
}
