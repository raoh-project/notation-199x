package notation199x

import "testing"

// The function of a form answers what Normalize and NormalizeWithin answer of the form, of text
// that each form changes in its own way: a mark after a starter, a compatibility ligature, a
// precomposed letter, Hangul, and text that is all ASCII.
func TestTheFunctionOfAFormAnswersWhatNormalizeAnswersOfIt(t *testing.T) {
	forms := []struct {
		form   Form
		whole  func(string) string
		within func(string, int) (string, bool)
	}{
		{NFC, NormalizeNFC, NormalizeNFCWithin},
		{NFD, NormalizeNFD, NormalizeNFDWithin},
		{NFKC, NormalizeNFKC, NormalizeNFKCWithin},
		{NFKD, NormalizeNFKD, NormalizeNFKDWithin},
	}
	texts := []string{"", "ASCII", "é", "é", "ﬁ́", "①", "각", "각", "á̖"}
	for _, each := range forms {
		for _, text := range texts {
			if got, want := each.whole(text), Normalize(each.form, text); got != want {
				t.Errorf("%v %q is %q, where Normalize answers %q", each.form, text, got, want)
			}
			for longest := -1; longest <= 4; longest++ {
				got, gotOk := each.within(text, longest)
				want, wantOk := NormalizeWithin(each.form, text, longest)
				if got != want || gotOk != wantOk {
					t.Errorf("%v %q within %d is %q, %v, where NormalizeWithin answers %q, %v",
						each.form, text, longest, got, gotOk, want, wantOk)
				}
			}
		}
	}
}
