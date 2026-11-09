package notation199x

import (
	"strings"
	"unicode/utf8"
)

// Form is one of the four normalization forms of UAX #15.
type Form uint8

const (
	// NFC is canonical decomposition, then canonical composition.
	NFC Form = iota
	// NFD is canonical decomposition.
	NFD
	// NFKC is compatibility decomposition, then canonical composition.
	NFKC
	// NFKD is compatibility decomposition.
	NFKD
)

// formFacts is what the algorithm asks of a form. What a form reads of the tables is held in the
// functions it names and in nothing else the algorithm runs, so that a program that asks for one
// form is linked with what that form reads: a function is linked where something the program
// reaches names it, and a form's facts are named where the program asks for the form.
type formFacts struct {
	// compatibility is the compatibility mappings, which the compatibility forms follow as well as
	// the canonical, and nil in a form that follows only the canonical.
	compatibility *mapping
	// compose is the primary composite of a starter and a code point after it, nil in a form that
	// does not compose.
	compose func(starter, r rune) (rune, bool)
	// trivialLimit is the code point below which every code point is a stable starter of the
	// form, which is known without asking a table. Every form's is past ASCII and before the
	// surrogates, which the generator checks.
	trivialLimit rune
	// stableBit is the form's bit of stable.
	stableBit uint8
}

var (
	nfcFacts  = formFacts{nil, compose, nfcTrivialLimit, 1}
	nfdFacts  = formFacts{nil, nil, nfdTrivialLimit, 2}
	nfkcFacts = formFacts{&compatibilityDecomposition, compose, nfkcTrivialLimit, 4}
	nfkdFacts = formFacts{&compatibilityDecomposition, nil, nfkdTrivialLimit, 8}
)

// forms is each form's facts, which asking for a form by its number links all of.
var forms = [...]*formFacts{NFC: &nfcFacts, NFD: &nfdFacts, NFKC: &nfkcFacts, NFKD: &nfkdFacts}

var formNames = [...]string{NFC: "NFC", NFD: "NFD", NFKC: "NFKC", NFKD: "NFKD"}

// stable is whether r is a stable starter of the form: a starter whose quick check for the form
// is Yes. Text made only of them is its own normalization in the form, and one of them ends what
// comes before it: no mark after it is put in order before it or composes with a starter before
// it, and it composes with nothing before it.
func (f *formFacts) stable(r rune) bool {
	return r < f.trivialLimit || stable.get(r)&f.stableBit != 0
}

// stableUpTo is where the stable starters s has from at end: the first code point from there that
// is not one, or the end of the text, or, where left is not negative, where it has gone past one
// more than left of them; how many code points it went past; and where the last of them begins,
// which is at where it went past none.
func (f *formFacts) stableUpTo(s string, at, left int) (end, count, last int) {
	last = at
	for at < len(s) && (left < 0 || count <= left) {
		stop := scanStop(s, at, left, count)
		for at < stop {
			if c := s[at]; c < utf8.RuneSelf {
				last = at
				at++
				count++
				continue
			}
			r, size := utf8.DecodeRuneInString(s[at:])
			if !f.stable(r) {
				return at, count, last
			}
			last = at
			at += size
			count++
		}
	}
	return at, count, last
}

// scanStop is how far a scan of s from at, which may go past one more than left code points and
// has gone past count, can go without counting them: a code point is a byte or more, so up to
// there it goes past no more than that, and a code point past ASCII can leave it short, where the
// scan goes on from there. What is left is held against the bytes left before anything is added
// to at, so that no bound, however large, takes it past the end. A negative left is no bound.
func scanStop(s string, at, left, count int) int {
	if left < 0 || left-count >= len(s)-at {
		return len(s)
	}
	return at + left - count + 1
}

// facts is what the algorithm asks of f, which panics where f is none of the four forms.
func (f Form) facts() *formFacts {
	if int(f) >= len(forms) {
		noneOf("Form", uint8(f))
	}
	return forms[f]
}

// String is the form's name, NFC, NFD, NFKC or NFKD, or Form(n) where it is none of them.
func (f Form) String() string {
	if int(f) < len(forms) {
		return formNames[f]
	}
	return nameOf(nil, "Form", uint8(f))
}

// Normalize is s in form, by Unicode 18.0.0's data.
//
// Not golang.org/x/text/unicode/norm, which answers for the Unicode version it was built with, and
// moves with a dependency update.
//
// The algorithm is the standard three steps of UAX #15: decompose fully, by the tables and by
// Hangul's arithmetic, put combining marks in canonical order, and in a composing form compose
// canonically wherever nothing blocks it. The compatibility forms decompose by the compatibility
// mappings as well as the canonical ones; composition is canonical in every form. s is valid UTF-8
// (see [InvalidUTF8At]). Normalize panics where form is none of the four forms.
func Normalize(form Form, s string) string {
	normalized, _ := normalize(form.facts(), s, -1)
	return normalized
}

// Normalizer normalizes in the one form it was made for, which the program that makes it names
// where it is written and not where it runs. A program that makes only an [NFCNormalizer] or an
// [NFDNormalizer] is linked without the compatibility mappings, and one that makes only an
// [NFDNormalizer] or an [NFKDNormalizer] without the composition tables, which [Normalize], that
// takes the form as a value, cannot do. The zero Normalizer is none of them, and panics where it is
// used.
type Normalizer struct {
	facts *formFacts
}

// NFCNormalizer is the Normalizer of [NFC].
func NFCNormalizer() Normalizer { return Normalizer{&nfcFacts} }

// NFDNormalizer is the Normalizer of [NFD].
func NFDNormalizer() Normalizer { return Normalizer{&nfdFacts} }

// NFKCNormalizer is the Normalizer of [NFKC].
func NFKCNormalizer() Normalizer { return Normalizer{&nfkcFacts} }

// NFKDNormalizer is the Normalizer of [NFKD].
func NFKDNormalizer() Normalizer { return Normalizer{&nfkdFacts} }

// Normalize is s in the form n was made for, as [Normalize] is.
func (n Normalizer) Normalize(s string) string {
	normalized, _ := normalize(n.of(), s, -1)
	return normalized
}

// NormalizeWithin is s in the form n was made for where that is no longer than longest scalar
// values, as [NormalizeWithin] is.
func (n Normalizer) NormalizeWithin(s string, longest int) (string, bool) {
	facts := n.of()
	if longest < 0 {
		return "", false
	}
	return normalize(facts, s, longest)
}

// of is the facts of the form n was made for, which panics where n is the zero Normalizer.
func (n Normalizer) of() *formFacts {
	if n.facts == nil {
		panic("notation199x: the zero Normalizer is none of the forms; make one with NFCNormalizer, NFDNormalizer, NFKCNormalizer or NFKDNormalizer")
	}
	return n.facts
}

// NormalizeWithin is [Normalize] where that is no longer than longest scalar values, and false
// where it is longer, which is found out before more than longest is written. What it holds and
// how much of s it reads turn on longest and not on the length of s: it reads no further once what
// it has read shows the answer to be longer. A negative bound is one no text is within.
// NormalizeWithin panics where form is none of the four forms.
func NormalizeWithin(form Form, s string, longest int) (string, bool) {
	facts := form.facts()
	if longest < 0 {
		return "", false
	}
	return normalize(facts, s, longest)
}

// normalize is s in form, and false where that is longer than longest; a negative longest is no
// bound.
//
// Text that is its own normalization is answered with itself. The text is kept as it is up to the
// first code point that is not a stable starter of the form. From the stable starter before that
// one, which what follows it may compose with, the algorithm is run up to the next stable starter,
// and the text is kept as it is again from there. A stable starter composes with nothing before it
// and blocks every mark after it from reaching a starter before it, so what comes before it is
// settled when it is read. A run is written into the answer only where it changed what it went
// over, and the answer is made only once one has: up to there it is the text.
//
// Each stable starter read is at least one code point of the answer, as the starter it is or
// composed with what follows it, so with a bound the text is read no further once those read and
// those before them are more than longest.
func normalize(facts *formFacts, s string, longest int) (string, bool) {
	var out strings.Builder
	// changed is whether a run has changed what it went over, and out holds the answer up to kept.
	changed := false
	kept, start := 0, 0
	// before is the code points of the answer before the last run the algorithm went over, and
	// read the stable starters read since, the last of them at start where start is before at.
	before, read := 0, 0
	var c *composing
	for at := 0; at < len(s); {
		// What is written and read is never more than longest, so what is left is never negative.
		left := -1
		if longest >= 0 {
			left = longest - before - read
		}
		if end, count, last := facts.stableUpTo(s, at, left); end > at {
			read += count
			if longest >= 0 && before+read > longest {
				return "", false
			}
			start = last
			at = end
			if at == len(s) {
				break
			}
		}
		if start < at {
			read--
		}
		if c == nil {
			c = newComposing(facts, longest)
		}
		end, ok := c.run(s, start, at, before+read)
		if !ok {
			return "", false
		}
		if string(c.out) != s[start:end] {
			if !changed {
				out.Grow(room(len(s), longest))
				changed = true
			}
			out.WriteString(s[kept:start])
			out.Write(c.out)
			kept = end
		}
		before = c.written
		read = 0
		start = end
		at = end
	}
	if !changed {
		return s, true
	}
	out.WriteString(s[kept:])
	return out.String(), true
}

// fewMarks is how many marks a run may hold before they are put in order by counting rather than
// by insertion, which is quadratic in the run.
const fewMarks = 32

// composing is one pass of canonical ordering and, where the form composes, composition over code
// points already decomposed: the starter of the run it is in, the marks held after it, and what is
// settled.
//
// With a bound, what is written and the least of the answer what is held can come to are no more
// than longest, which take holds to before it holds another mark: the starter, and the marks but
// those it may compose with, which are no more than mostMarksComposed. So a run holds no more than
// longest and mostMarksComposed marks, whatever the length of the text.
type composing struct {
	form    *formFacts
	longest int
	// out is what run wrote, the run settled.
	out []byte
	// written is how many code points of the answer come before what is held: those before the
	// run, and those it has written.
	written int
	starter rune // -1 where the run has none
	marks   []rune
	// parts is what one code point decomposes into.
	parts []rune
	// Room each of out, marks and parts starts in, made with the composing, so that a run that
	// fits it allocates nothing of its own. Every decomposition fits partsRoom.
	outRoom   [64]byte
	marksRoom [8]rune
	partsRoom [longestDecomposition]rune
}

func newComposing(form *formFacts, longest int) *composing {
	c := &composing{form: form, longest: longest, starter: -1}
	c.out = c.outRoom[:0]
	c.marks = c.marksRoom[:0]
	c.parts = c.partsRoom[:0]
	return c
}

// run runs the algorithm over s from start, before code points of the answer coming before it, up
// to the first stable starter after at, or to the end of the text where none comes, and writes the
// run settled into out. The code points from start to at are taken, and so is the one there,
// whatever they are; with at the end of the text, all of it from start is. It is where it stopped,
// and false where the answer has passed longest.
func (c *composing) run(s string, start, at int, before int) (int, bool) {
	c.out = c.out[:0]
	c.written = before
	j := start
	for j < len(s) {
		r, size := utf8.DecodeRuneInString(s[j:])
		if j > at && c.form.stable(r) {
			break
		}
		j += size
		var decomposed bool
		if c.parts, decomposed = decomposeInto(c.parts[:0], r, c.form.compatibility); !decomposed {
			if !c.take(r) {
				return 0, false
			}
			continue
		}
		for _, part := range c.parts {
			if !c.take(part) {
				return 0, false
			}
		}
	}
	if !c.write(c.settle()) {
		return 0, false
	}
	return j, true
}

// take takes the next decomposed code point, and is false where what is written has passed
// longest.
func (c *composing) take(r rune) bool {
	if combiningClass.get(r) != 0 {
		if c.longest >= 0 && c.leastHeld(len(c.marks)+1) > c.longest-c.written {
			return false
		}
		c.marks = append(c.marks, r)
		return true
	}
	kept := c.settle()
	if c.form.compose != nil && c.starter >= 0 && kept == 0 {
		if composed, ok := c.form.compose(c.starter, r); ok {
			c.starter = composed
			return true
		}
	}
	if !c.write(kept) {
		return false
	}
	c.starter = r
	return true
}

// leastHeld is the least number of code points of the answer the starter held and marks marks
// after it come to, whatever follows: every mark, and the starter, but those of the marks it may
// compose with in a composing form.
func (c *composing) leastHeld(marks int) int {
	if c.starter < 0 {
		return marks
	}
	if c.form.compose != nil {
		marks = max(marks-mostMarksComposed, 0)
	}
	return 1 + marks
}

// settle puts the held marks in canonical order and, where the form composes, composes into the
// starter each one nothing blocks, and answers how many marks are left after the starter.
func (c *composing) settle() int {
	if len(c.marks) > 1 {
		c.order()
	}
	if c.form.compose == nil || c.starter < 0 {
		return len(c.marks)
	}
	kept := 0
	lastClass := -1
	for _, mark := range c.marks {
		class := int(combiningClass.get(mark))
		if lastClass < class {
			if composed, ok := c.form.compose(c.starter, mark); ok {
				c.starter = composed
				continue
			}
		}
		c.marks[kept] = mark
		kept++
		lastClass = class
	}
	c.marks = c.marks[:kept]
	return kept
}

// order puts the held marks in canonical order: stable, by combining class.
func (c *composing) order() {
	if len(c.marks) <= fewMarks {
		for i := 1; i < len(c.marks); i++ {
			mark := c.marks[i]
			class := combiningClass.get(mark)
			j := i
			for j > 0 && combiningClass.get(c.marks[j-1]) > class {
				c.marks[j] = c.marks[j-1]
				j--
			}
			c.marks[j] = mark
		}
		return
	}
	var starts [257]int
	for _, mark := range c.marks {
		starts[int(combiningClass.get(mark))+1]++
	}
	for class := 1; class < len(starts); class++ {
		starts[class] += starts[class-1]
	}
	ordered := make([]rune, len(c.marks))
	for _, mark := range c.marks {
		class := combiningClass.get(mark)
		ordered[starts[class]] = mark
		starts[class]++
	}
	c.marks = ordered
}

// write writes the starter and the kept marks after it, and empties the run.
func (c *composing) write(kept int) bool {
	if c.starter >= 0 && !c.writeOne(c.starter) {
		return false
	}
	for _, mark := range c.marks[:kept] {
		if !c.writeOne(mark) {
			return false
		}
	}
	c.starter = -1
	c.marks = c.marks[:0]
	return true
}

func (c *composing) writeOne(r rune) bool {
	if c.longest >= 0 && c.written >= c.longest {
		return false
	}
	c.out = utf8.AppendRune(c.out, r)
	c.written++
	return true
}

// Hangul's algorithmic decomposition and composition, as UAX #15 states them.
const (
	hangulSBase  = 0xAC00
	hangulLBase  = 0x1100
	hangulVBase  = 0x1161
	hangulTBase  = 0x11A7
	hangulLCount = 19
	hangulVCount = 21
	hangulTCount = 28
	hangulNCount = hangulVCount * hangulTCount
	hangulSCount = hangulLCount * hangulNCount
)

func isHangulSyllable(r rune) bool {
	return r >= hangulSBase && r < hangulSBase+hangulSCount
}

// decomposeInto appends r's full decomposition to dst, and is false, dst as it was, where r is its
// own: Hangul's arithmetic split, or the one-step decompositions followed as far as they go, since
// a decomposition may map to code points that decompose themselves. With compatibility not nil the
// compatibility mappings are followed as well.
func decomposeInto(dst []rune, r rune, compatibility *mapping) ([]rune, bool) {
	if isHangulSyllable(r) {
		index := r - hangulSBase
		dst = append(dst, hangulLBase+index/hangulNCount, hangulVBase+(index%hangulNCount)/hangulTCount)
		if t := hangulTBase + index%hangulTCount; t != hangulTBase {
			dst = append(dst, t)
		}
		return dst, true
	}
	mapped := decompositionOf(r, compatibility)
	if mapped == nil {
		return dst, false
	}
	for _, part := range mapped {
		var further bool
		if dst, further = decomposeInto(dst, part, compatibility); !further {
			dst = append(dst, part)
		}
	}
	return dst, true
}

// decompositionOf is r's one-step decomposition by canonicalDecomposition, or where compatibility
// is not nil by compatibility as well, read where decompositionPosition says it is; nil where it
// has none.
func decompositionOf(r rune, compatibility *mapping) []rune {
	at := int(decompositionPosition.get(r))
	switch {
	case at == 0:
		return nil
	case at <= len(canonicalDecomposition):
		return canonicalDecomposition[at-1].to
	case compatibility != nil:
		return (*compatibility)[at-len(canonicalDecomposition)-1].to
	}
	return nil
}

// compose is the primary composite of starter followed by r, and false where the pair does not
// compose: Hangul's L+V and LV+T, or compositionPairs in the row of starter, found by the column
// of r.
func compose(starter, r rune) (rune, bool) {
	if starter >= hangulLBase && starter < hangulLBase+hangulLCount &&
		r >= hangulVBase && r < hangulVBase+hangulVCount {
		return hangulSBase + ((starter-hangulLBase)*hangulVCount+(r-hangulVBase))*hangulTCount, true
	}
	if isHangulSyllable(starter) && (starter-hangulSBase)%hangulTCount == 0 &&
		r > hangulTBase && r < hangulTBase+hangulTCount {
		return starter + (r - hangulTBase), true
	}
	row := int(compositionFirst.get(starter))
	column := uint32(compositionSecond.get(r))
	if row == 0 || column == 0 {
		return 0, false
	}
	// A row is a few pairs, so it is read through rather than searched.
	for _, pair := range compositionPairs[compositionRows[row-1]:compositionRows[row]] {
		if pair&0xFF == column {
			return rune(pair >> 8), true
		}
	}
	return 0, false
}
