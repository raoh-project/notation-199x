package notation199x

import (
	"strings"
	"testing"
)

// A Normalizer of a form answers what Normalize and NormalizeWithin answer of the form, of text that
// each form changes in its own way: a mark after a starter, a compatibility ligature, a precomposed
// letter, Hangul, and text that is all ASCII.
func TestANormalizerAnswersWhatNormalizeAnswersOfItsForm(t *testing.T) {
	forms := []struct {
		form       Form
		normalizer Normalizer
	}{
		{NFC, NFCNormalizer()},
		{NFD, NFDNormalizer()},
		{NFKC, NFKCNormalizer()},
		{NFKD, NFKDNormalizer()},
	}
	texts := []string{"", "ASCII", "é", "é", "ﬁ́", "①", "각", "각", "á̖"}
	for _, each := range forms {
		for _, text := range texts {
			if got, want := each.normalizer.Normalize(text), Normalize(each.form, text); got != want {
				t.Errorf("%v Normalize(%q) = %q, where Normalize answers %q", each.form, text, got, want)
			}
			for longest := 0; longest <= 4; longest++ {
				got, gotOk := each.normalizer.NormalizeWithin(text, longest)
				want, wantOk := NormalizeWithin(each.form, text, longest)
				if got != want || gotOk != wantOk {
					t.Errorf("%v NormalizeWithin(%q, %d) = %q, %v, where NormalizeWithin answers %q, %v",
						each.form, text, longest, got, gotOk, want, wantOk)
				}
			}
		}
	}
}

// The zero Normalizer is none of the forms, which it says when it is used, and not as a nil pointer.
func TestTheZeroNormalizerSaysWhatToMakeOneWith(t *testing.T) {
	defer func() {
		message, _ := recover().(string)
		if !strings.Contains(message, "NFCNormalizer") {
			t.Errorf("the zero Normalizer panicked with %q, which does not say what to make one with", message)
		}
	}()
	Normalizer{}.Normalize("x")
}
