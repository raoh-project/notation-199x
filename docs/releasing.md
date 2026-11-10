# Releasing

Each implementation is released on its own, and its version is its own: it says what changed in
that implementation's API, and a Java release and a Go release with the same number have nothing
to do with each other. What the implementations agree on is held by the commit, the pinned Unicode
version and `suite/`, not by a version number.

## The version on develop

After a release X.Y.Z of an implementation, `develop` holds the patch after it: `X.Y.(Z+1)-SNAPSHOT`
for Java, and `X.Y.(Z+1)-dev` for the npm package and the crate. Go and PHP take their version from a
tag and name none in their files. A release that is more than a patch sets its own version when it
is cut, in the release's first step. `<next version>` below is that patch.

## The release pull request

A release is a pull request from `develop` to `main`, and `main` holds the last release, so the
benchmarks of `develop` are built on a crate that is not what they were written for: they use what
the crate has gained since. The `rust-perf` job cannot build them on `main` and fails, and a commit
of the branch says so with the line `Benchmark-baseline: none` in its message, as
`rust/bench/README.md` says of a pull request that needs what its base does not have. What each
change did to the counts was compared when it was made, in the pull request that made it, and the
release pull request is run against itself.

## Java

`develop` holds the next version as a snapshot, `X.Y.Z-SNAPSHOT`, and a release is that version
without the suffix. A snapshot is deployed from anywhere with `cd java && mvn clean deploy`. A
release is deployed from the commit its tag names, so what Central holds is what the tag holds:

1. On a branch from `develop`, set the version to the release and open a pull request to `main`:
   ```sh
   cd java && mvn versions:set -DnewVersion=X.Y.Z -DgenerateBackupPoms=false
   ```
2. Merge it, and tag the merge commit on `main` `vX.Y.Z`. CI fails a tag that is not `v` and a
   version, that names a commit not on `main`, that is not the version of the project, or whose
   version is a snapshot.
3. Deploy from a checkout of the tag:
   ```sh
   git checkout vX.Y.Z
   cd java && mvn -Prelease clean deploy
   ```
   The `release` profile refuses a snapshot version, attaches the sources and the Javadoc, and
   signs everything, and the Central Portal publishes the release once it has validated it.
   `clean`, because NullAway runs inside javac and a compile Maven thinks is up to date is not
   checked.

   With Maven 3.10.0 and central-publishing-maven-plugin 0.10.0, the Portal refused the bundle of
   0.2.0 (`Bundle has content that does NOT have a .pom file`): it held `maven-metadata-local.xml`
   and `_remote.repositories` files, which a local repository writes. 0.2.0 was published by
   uploading `java/target/central-publishing/central-bundle.zip` on the Portal by hand, with those
   files taken out:
   ```sh
   zip -d central-bundle.zip '*maven-metadata-local.xml' '*_remote.repositories*'
   ```
   [#66](https://github.com/raoh-project/notation-199x/issues/66) is to find out whether a later
   plugin leaves them out.
4. Merge `main` back into `develop`, so that the next release's pull request starts from this one,
   and set `develop` to the next snapshot:
   ```sh
   cd java && mvn versions:set -DnewVersion=<next version>-SNAPSHOT -DgenerateBackupPoms=false
   ```

## Go

The module is `github.com/raoh-project/notation-199x/go`, and a release of it is a tag: nothing in
`go/` names its version. A tag on a module in a subdirectory begins with the subdirectory, so the
module's version `vX.Y.Z` is the tag `go/vX.Y.Z`. The first release is `go/v0.2.0`.

1. Merge what is to be released into `main`, as for a Java release.
2. Tag the commit on `main` `go/vX.Y.Z` and push the tag. CI runs the Go tests on it, and fails a
   tag that is not `go/` and a version, or that names a commit not on `main`.
3. Ask the module proxy for the version, so that it holds what the tag holds from then on:
   ```sh
   GOPROXY=https://proxy.golang.org go list -m github.com/raoh-project/notation-199x/go@vX.Y.Z
   ```

A tag that has been pushed is never moved: the proxy and the checksum database keep the first
contents a version had, and a module fetched by version is checked against them.

## PHP

The package is `raoh/notation-199x`, and a release of it is a tag: nothing in `php/` names its
version. Packagist reads `composer.json` at the root of a repository, and this one's is in `php/`,
so a release is published to a mirror whose root is `php/`,
[raoh-project/notation-199x-php](https://github.com/raoh-project/notation-199x-php), and Packagist
reads the mirror. The mirror is written by CI and by nothing else: nobody commits to it, and an
issue or a pull request there is sent here. As for Go, a tag here begins with the directory, so the
package's version `vX.Y.Z` is the tag `php/vX.Y.Z` here and the tag `vX.Y.Z` on the mirror. The
first release is `php/v0.2.0`.

1. Merge what is to be released into `main`, as for a Java release.
2. Tag the commit on `main` `php/vX.Y.Z` and push the tag. The `PHP release` workflow fails a tag
   that is not `php/` and a version, or that names a commit not on `main`. It runs the whole of CI
   on the commit, and only once that passes writes the files git tracks under `php/` and the
   license as one commit on the mirror's `main`, and tags it `vX.Y.Z` with a message naming the
   commit here it was written from.
3. Packagist takes the version from the mirror's tag, by the mirror's webhook.

A tag that has been pushed is never moved, here or on the mirror: Packagist keeps the commit a
version was first published at, and the workflow refuses a version the mirror already has. A
release that is wrong is followed by another.

The workflow pushes to the mirror with a deploy key that can write to it, held here as the secret
`PHP_MIRROR_DEPLOY_KEY`. Setting up the mirror is done once: create
`raoh-project/notation-199x-php` empty, add the public half of the key to it as a deploy key with
write access and the private half here as that secret, push the first tag, and submit the mirror
to Packagist under the `raoh` vendor, with its GitHub hook on. A deploy key is added only where the
organization allows them (`deploy_keys_enabled_for_repositories` of `raoh-project`), and GitHub
refuses one with `Deploy keys are disabled for this repository` otherwise. The workflow logs the
public half of the key it is handed, so a run that is refused shows which key the mirror is to
have.

## TypeScript

The package is `@raoh/notation-199x`, published from `ts/` by the `TypeScript publish` workflow and by
nothing else. `ts/package.json` on `develop` holds the next version as `X.Y.Z-dev`. A push to
`develop` that changes `ts/` publishes the commit it brings, once CI has passed on it, as
`X.Y.Z-dev.N.YYYYMMDDHHMMSS.gHHHHHHHHHHHH` under the dist-tag `dev`: `N` is how many commits the
commit holds, itself and every one before it, then the time of the commit, then the commit. npm
takes a version once and never again, so a development version is a commit, as a timestamped Maven
snapshot is. A later commit on `develop` holds every one before it, so its `N` is greater and SemVer
orders the versions as `develop` does; the time does not, since a commit can be dated before its
parent, and two can be made in one second.

`dev` names the newest state of the package on `develop`, and not whichever run happened to publish
last. Just before it publishes, a run asks `develop` as it is then whether a later commit that
changes `ts/` has reached it, and where one has, it publishes nothing and leaves `dev` to that
commit's run; one run publishes at a time, so the run that finds itself newest publishes before any
later commit's run asks, whatever order GitHub starts the runs in. So the commits of one push but
the last, and a push a later one overtakes before its run publishes, have no version of their own;
and where the later commit's run fails, `dev` stays where it was until a push whose run passes.

`npm install @raoh/notation-199x` takes `latest`, which is a release, and a range written for
releases, `^0.2.0`, takes no development version: npm takes a prerelease only for a range that names
one of the same `X.Y.Z`, and `^0.2.0-dev.1` takes every later one. So a project that
means one commit writes its version exactly, `npm install --save-exact @raoh/notation-199x@<version>`,
as `npm install` otherwise saves a range.

`ts/scripts/publish.sh` says what version a ref makes, under which dist-tag, and publishes it, and CI
takes each path it can take as a dry run on every pull request (`ts/scripts/try-publish.sh`), so
each is taken before a push takes it. A release is a tag, as for Go and PHP, and the tag begins with
the directory:

1. On a branch from `develop`, set `ts/package.json`'s version to `X.Y.Z` and open a pull request to
   `main`, as for a Java release.
2. Merge it, and tag the merge commit on `main` `ts/vX.Y.Z`. The workflow fails a tag that is not
   `ts/` and a version, that is not the version `ts/package.json` holds, or that names a commit not
   on `main`. It runs the whole of CI on the commit, and only once that passes publishes `X.Y.Z`:
   under `latest` where it is newer than what `latest` names as it is published, and otherwise, as a
   patch of an older line or one whose run comes after a newer release's, under `release-X.Y.Z`,
   which names it and nothing else. `latest` only ever moves to a newer release, so once every run
   has run it names the newest release published, whatever order the runs ran in. What it names is
   asked of the registry, which holds what was published, so a tag the workflow refused, or whose run
   failed, has no say in it.
3. Merge `main` back into `develop`, and set `ts/package.json` there to `<next version>-dev`.

The workflow logs in with nothing: npm proves to the registry that it runs in this workflow of this
repository, which the package's settings on npmjs.com name as its trusted publisher, and the
registry records with each version the commit and the run it was built in. A trusted publisher is
named for a package that exists, so the package's first version is published by hand from a
checkout, and the trusted publisher named after it: repository `raoh-project/notation-199x`, workflow
`ts-publish.yml`, with `npm publish` allowed. A trusted publisher named since September 3, 2026
allows `npm stage publish` and allows `npm publish` only where it is chosen, and the workflow
publishes with `npm publish`. It needs nothing else: it reads what `latest` names from the registry
without logging in, as anyone can of a public package.

A version published with `npm stage publish` waits on npmjs.com until it is approved, and the
registry does not answer for it until then; npm still counts the version as taken. So where a
release is staged by hand, it is approved before its tag is pushed: the workflow, finding no such
version in the registry, publishes it, and npm refuses with `You cannot publish over the previously
published versions`.

## Rust

The crate is `notation199x`, published to crates.io from `rust/` by the `Rust release` workflow and by
nothing else. `rust/Cargo.toml` on `develop` holds the next version as `X.Y.Z-dev`, and a release is
that version without the suffix. As for Go and PHP, a tag begins with the directory, `rust/vX.Y.Z`.

1. On a branch from `develop`, set `rust/Cargo.toml`'s version to `X.Y.Z`, let `cargo check` write it
   into `rust/Cargo.lock`, and open a pull request to `main`, as for a Java release.
2. Merge it, and tag the merge commit on `main` `rust/vX.Y.Z` and push the tag. The workflow fails a
   tag that is not `rust/` and a version, that names a commit not on `main`, or that is not the
   version `rust/Cargo.toml` holds, which a `-dev` version never is. It runs the whole of CI on the
   commit, and only once that passes publishes the crate and writes the GitHub release of the tag.
3. Merge `main` back into `develop`, and set `rust/Cargo.toml` there to `<next version>-dev`.

`cargo publish --dry-run` packages the crate and builds it from the package first.

A version crates.io has taken is never replaced: a release that is wrong is yanked and followed by
another.

The crate's version says neither the Unicode version it implements nor the image formats it reads and
writes. `[package.metadata.notation199x]` in `rust/Cargo.toml` says them (`unicode`, `images-read`,
`images-written`), so that crates.io holds them with each version. `rust/tests/release_metadata.rs`
holds what can be asked of the crate to what the repository establishes: a `unicode` that is not the
version `ucd/` holds, or that the description and the documentation do not say, fails, and so does
an `images-read` that is not the formats `Pattern::from_image` accepts an image of, and an
`images-written` that names a format `image/` does not define. What a crate writes cannot be asked of
it, so nothing finds a writer that is added: whoever adds one adds its format to `images-written` in
the same commit, with a test that reads back what it writes. The workflow writes the same three lines
as the notes of the GitHub release, which is not marked as the latest release of the repository, since
the implementations are released on their own. A release that already exists as a draft or a
prerelease, or with other notes, fails the workflow and is put right by hand.

When the crate is published and the release is not written, run the `release` job again. When a run
fails after crates.io has taken the version, so that a run of the publish job finds it taken, check
that what crates.io holds is what the tag holds (`cargo package` at the tag and
`cargo download`-ing or unpacking the crate from crates.io, and comparing the two), and then write
the release by hand with `gh release create rust/vX.Y.Z --verify-tag --latest=false` and the notes the
workflow would write. A version that is taken is never published again, and the workflow does not
take "already published" for "published".

The workflow does not keep a token: crates.io proves the run to be this workflow of this repository,
which the crate's settings on crates.io name as its trusted publisher (repository
`raoh-project/notation-199x`, workflow `rust-release.yml`), and gives it a token for the run. A
trusted publisher is named for a crate that exists, so the first version, 0.2.0, was published by hand
and the trusted publisher is named once, by the owner of the crate.
