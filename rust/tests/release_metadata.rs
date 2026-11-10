//! `[package.metadata.notation199x]` in `Cargo.toml`, which a release writes into its notes and
//! crates.io keeps with the version, says what the crate implements. Each claim in it is held to what
//! the repository establishes, so that it cannot go on saying what the crate no longer does.

mod common;

use common::{each_line, repository_file};
use notation199x::Pattern;
use std::collections::BTreeSet;
use std::fs;
use std::path::Path;

const MANIFEST: &str = include_str!("../Cargo.toml");

/// The values of `key` in the table, from the line `key = "value"` or `key = ["a", "b"]`.
fn metadata(key: &str) -> Vec<String> {
    let table = MANIFEST
        .split("[package.metadata.notation199x]")
        .nth(1)
        .expect("Cargo.toml has the table [package.metadata.notation199x]");
    let table = table.split("\n[").next().unwrap();
    let line = table
        .lines()
        .find_map(|line| line.strip_prefix(key)?.trim_start().strip_prefix('='))
        .unwrap_or_else(|| panic!("the table has no `{key}`"));
    line.trim()
        .trim_matches(['[', ']'])
        .split(',')
        .map(|value| value.trim().trim_matches('"').to_string())
        .filter(|value| !value.is_empty())
        .collect()
}

/// The Unicode version the crate says it implements is the version `ucd/` holds, and what the crate
/// says of itself elsewhere, in its description and its documentation, says the same.
#[test]
fn the_unicode_version_is_the_one_ucd_holds() {
    let named = metadata("unicode");
    assert_eq!(named.len(), 1, "one Unicode version: {named:?}");
    let named = &named[0];

    for (what, text) in [
        ("the description in Cargo.toml", MANIFEST),
        (
            "the documentation in src/lib.rs",
            include_str!("../src/lib.rs"),
        ),
    ] {
        assert!(
            text.contains(&format!("Unicode {named}")),
            "{what} does not say Unicode {named}"
        );
    }

    let Some(ucd) = repository_file("ucd") else {
        return;
    };
    let held: Vec<String> = fs::read_dir(ucd)
        .unwrap()
        .map(|entry| entry.unwrap())
        .filter(|entry| entry.path().is_dir())
        .map(|entry| entry.file_name().into_string().unwrap())
        .collect();
    assert_eq!(
        held,
        std::slice::from_ref(named),
        "ucd/ holds the version the crate names"
    );
}

/// The formats the crate reads are the formats the repository defines whose images it accepts: a
/// format is read where `from_image` accepts an image the format's fixtures hold, so a format added
/// to `image/` and read without being named here, or named and not read, fails.
#[test]
fn the_images_read_are_the_formats_from_image_accepts() {
    let Some(image) = repository_file("image") else {
        return;
    };
    let mut read = BTreeSet::new();
    for entry in fs::read_dir(&image).unwrap() {
        let path = entry.unwrap().path();
        let Some(format) = path
            .file_name()
            .and_then(|name| name.to_str())
            .and_then(|name| name.strip_suffix(".md"))
            .filter(|name| name.starts_with('P'))
        else {
            continue;
        };
        let fixtures = format!("image/{}.txt", format.to_lowercase());
        let mut accepted = false;
        each_line(&fixtures, 3, |line| {
            let image = line.text_as_shown(0);
            line.take(1);
            line.take(2);
            accepted |= Pattern::from_image(&image).is_ok();
            None
        });
        if accepted {
            read.insert(format.to_string());
        }
    }
    let named: BTreeSet<String> = metadata("images-read").into_iter().collect();
    assert_eq!(
        named, read,
        "images-read names the formats from_image accepts"
    );
}

/// The crate writes no image, and the metadata says so. What a crate writes cannot be asked of it the
/// way what it reads can, so what is held is that no public function names an image but the one that
/// reads: a writer added to the crate fails here, and is to be named in `images-written` with a test
/// that reads back what it writes.
#[test]
fn the_crate_writes_no_image_and_says_so() {
    assert_eq!(metadata("images-written"), Vec::<String>::new());

    let mut named = Vec::new();
    let mut directories = vec![Path::new(env!("CARGO_MANIFEST_DIR")).join("src")];
    while let Some(directory) = directories.pop() {
        for entry in fs::read_dir(directory).unwrap() {
            let path = entry.unwrap().path();
            if path.is_dir() {
                directories.push(path);
            } else if path.extension().is_some_and(|extension| extension == "rs") {
                for line in fs::read_to_string(&path).unwrap().lines() {
                    let line = line.trim_start();
                    if let Some(signature) = line.strip_prefix("pub fn ") {
                        let name = signature.split(['(', '<']).next().unwrap();
                        if name.contains("image") {
                            named.push(name.to_string());
                        }
                    }
                }
            }
        }
    }
    assert_eq!(named, ["from_image"], "public functions that name an image");
}
