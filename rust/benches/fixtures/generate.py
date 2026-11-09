#!/usr/bin/env python3
"""Writes the texts the benchmarks read, from a fixed seed and fixed word lists.

The files are committed, and a change of a benchmark's input is a change of the files, so that two
commits are compared on the same text. Run it to see that the files are what it writes:

    python3 generate.py && git diff --exit-code .

No table of Unicode is read: every decomposed form is spelled out here, so that the text does not
move with the Python that runs this.
"""

import pathlib

HERE = pathlib.Path(__file__).parent
SCALARS = 4096  # about this many Unicode scalar values in each file


class Lcg:
    """A linear congruential generator, so that the order of the words is the same everywhere."""

    def __init__(self, seed):
        self.state = seed

    def below(self, n):
        self.state = (self.state * 6364136223846793005 + 1442695040888963407) % (1 << 64)
        return (self.state >> 33) % n


def make(words, seed, joiner=" "):
    rng = Lcg(seed)
    out, n = [], 0
    while n < SCALARS:
        word = words[rng.below(len(words))]
        out.append(word)
        n += len(word) + len(joiner)
    return joiner.join(out) + "\n"


def write(name, text):
    with open(HERE / name, "w", encoding="utf-8", newline="\n") as f:
        f.write(text)


ASCII_LOWER = "the quick brown fox jumps over a lazy dog while seven wizards box quietly".split()
ASCII_MIXED = [w.capitalize() if i % 3 == 0 else w.upper() if i % 5 == 0 else w
               for i, w in enumerate(ASCII_LOWER)]

# Precomposed Latin, each already in NFC, with the same words in lower and upper case.
LATIN_LOWER = ["café", "naïve", "façade", "über", "señor", "crème", "brûlée", "smörgåsbord",
               "jalapeño", "fiancée", "déjà", "vu", "coöperate", "résumé", "piñata", "doppelgänger"]
LATIN_MIXED = [w.upper() if i % 2 == 0 else w.capitalize() for i, w in enumerate(LATIN_LOWER)]

# The same Latin words with each letter that has a canonical decomposition written as the base and
# the mark, which is what NFC has to compose.
DECOMPOSE = {"é": "é", "ï": "ï", "ç": "ç", "ü": "ü", "ñ": "ñ",
             "è": "è", "û": "û", "ö": "ö", "å": "å", "à": "à",
             "ä": "ä"}
LATIN_DECOMPOSED = ["".join(DECOMPOSE.get(c, c) for c in w) for w in LATIN_LOWER]

GREEK_LOWER = ["άνθρωπος", "γλώσσα", "ελληνικά", "κόσμος", "οδός", "σοφία", "φιλοσοφία", "ψυχή",
               "ήλιος", "θάλασσα", "μητέρα", "νύχτα", "ωκεανός", "ίδιος", "αρχή", "τέλος"]
GREEK_UPPER = ["ΑΝΘΡΩΠΟΣ", "ΓΛΩΣΣΑ", "ΕΛΛΗΝΙΚΑ", "ΚΟΣΜΟΣ", "ΟΔΟΣ", "ΣΟΦΙΑ", "ΦΙΛΟΣΟΦΙΑ", "ΨΥΧΗ",
               "ΗΛΙΟΣ", "ΘΑΛΑΣΣΑ", "ΜΗΤΕΡΑ", "ΝΥΧΤΑ", "ΩΚΕΑΝΟΣ", "ΙΔΙΟΣ", "ΑΡΧΗ", "ΤΕΛΟΣ"]

JAPANESE = ["日本語", "文字", "正規化", "大文字", "小文字", "ひらがな", "カタカナ", "漢字",
            "東京", "京都", "桜", "富士山", "言葉", "仕様", "ソフトウェア", "テスト"]
# Halfwidth katakana, fullwidth Latin and a voiced mark written apart: what NFKC and NFC change.
JAPANESE_COMPAT = ["ｶﾀｶﾅ", "ＡＢＣ", "ｱｲｳｴｵ", "０１２", "が", "ざ", "ﾃｽﾄ", "ｿﾌﾄ"]

# A base letter with two or three marks, in an order that is not the canonical one, so that
# canonical ordering has to move them, and then composition has to join them.
COMBINING = ["ạ́", "ọ́", "ẹ́̂", "ǖ", "ị̈",
             "ṇ̃", "ḉ", "ṩ", "ỵ́", "ą́"]

write("ascii_lower.txt", make(ASCII_LOWER, 1))
write("ascii_mixed.txt", make(ASCII_MIXED, 2))
write("latin_lower.txt", make(LATIN_LOWER, 3))
write("latin_mixed.txt", make(LATIN_MIXED, 4))
write("latin_decomposed.txt", make(LATIN_DECOMPOSED, 3))
write("greek_lower.txt", make(GREEK_LOWER, 5))
write("greek_upper.txt", make(GREEK_UPPER, 6))
write("japanese.txt", make(JAPANESE, 7, joiner="・"))
write("japanese_compat.txt", make(JAPANESE_COMPAT, 8, joiner="・"))
write("combining.txt", make(COMBINING, 9, joiner=""))
