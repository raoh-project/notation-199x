#!/usr/bin/env bash
# Says whether a tag may name a release: <prefix>vX.Y.Z, on a commit that is on main. Prints X.Y.Z.
#
#   check-release-tag.sh <ref> <prefix>      prefix is '' for Java, 'go/', 'rust/'
#
# Needs the history of main, so the checkout has fetch-depth: 0. A release is cut from main, and a
# tag on a commit that never reached it is refused before anything is published from it.
set -euo pipefail

ref=${1:?usage: check-release-tag.sh <ref> <prefix>}
prefix=${2-}

tag=${ref#refs/tags/}
if ! printf '%s\n' "$tag" | grep -Eq "^${prefix}v(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\$"; then
  echo "::error::$tag is not ${prefix}vX.Y.Z" >&2
  exit 1
fi
if ! git merge-base --is-ancestor "${GITHUB_SHA:-$(git rev-parse HEAD)}" origin/main; then
  echo "::error::$tag names a commit that is not on main" >&2
  exit 1
fi
version=${tag#"${prefix}v"}
printf '%s\n' "$version"
