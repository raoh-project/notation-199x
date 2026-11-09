package notation199x

import (
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"strings"
	"testing"
)

// modulePath is this module's path, which the programs the test builds import.
const modulePath = "github.com/raoh-project/199x-notation/go"

// tableGroups is the symbols of the tables each part of the algorithm reads, by group.
var tableGroups = []struct {
	name    string
	symbols []string
}{
	{"canonical", []string{"canonicalDecomposition", "decompositionPosition", "combiningClass", "stable"}},
	{"compatibility", []string{"compatibilityDecomposition"}},
	{"composition", []string{"compositionFirst", "compositionSecond", "compositionRows", "compositionPairs"}},
	{"lower", []string{"lowerMapping", "finalSigmaMapping", "lowerASCII", "lowerPosition", "caseContext"}},
	{"upper", []string{"upperMapping", "upperASCII", "upperPosition"}},
}

// A program of the crate carries the tables of what it asks of the crate, and of nothing else: one
// that normalizes in NFC or NFD does not carry the compatibility mappings, one that normalizes in
// NFD or NFKD does not carry the composition tables, one that lowercases does not carry the
// uppercase tables, and one that asks nothing carries no table. What a statically linked program
// carries is what its size is, which the tables are most of.
//
// Each table is read off the linked program by its symbol, and the tables a program needs are held
// to be there, so that the test does not pass on a program whose symbols it cannot read.
func TestAProgramCarriesOnlyTheTablesItAsksFor(t *testing.T) {
	if raceEnabled {
		t.Skip("what a program carries is the same with the race detector, and is held without it")
	}
	goTool, err := exec.LookPath("go")
	if err != nil {
		goTool = filepath.Join(runtime.GOROOT(), "bin", "go")
		if _, err := os.Stat(goTool); err != nil {
			if os.Getenv(requireSuite) != "" {
				t.Fatalf("there is no go to build the programs with, and %s is set", requireSuite)
			}
			t.Skip("there is no go to build the programs with")
		}
	}
	module, err := filepath.Abs(".")
	if err != nil {
		t.Fatal(err)
	}
	dir := t.TempDir()
	write := func(name, text string) {
		t.Helper()
		if err := os.MkdirAll(filepath.Dir(filepath.Join(dir, name)), 0o755); err != nil {
			t.Fatal(err)
		}
		if err := os.WriteFile(filepath.Join(dir, name), []byte(text), 0o644); err != nil {
			t.Fatal(err)
		}
	}
	// The module is written by the tool that reads it, which takes the path of the library as an
	// argument and not as text of its own syntax, so that no path of the library's is one it cannot
	// read.
	write("go.mod", "module linked\n\ngo 1.23\n")
	edit := exec.Command(goTool, "mod", "edit", "-require="+modulePath+"@v0.0.0",
		"-replace="+modulePath+"="+module)
	edit.Dir = dir
	if out, err := edit.CombinedOutput(); err != nil {
		t.Fatalf("go mod edit: %v\n%s", err, out)
	}

	for _, each := range []struct {
		name string
		// call is what the program does with its argument, text, and the groups that needs.
		call   string
		groups string
	}{
		{"NFC", `n.NFCNormalizer().Normalize(text)`, "canonical composition"},
		{"NFC within", `n.NFCNormalizer().NormalizeWithin(text, 10)`, "canonical composition"},
		{"NFD", `n.NFDNormalizer().Normalize(text)`, "canonical"},
		{"NFKC", `n.NFKCNormalizer().Normalize(text)`, "canonical compatibility composition"},
		{"NFKD", `n.NFKDNormalizer().Normalize(text)`, "canonical compatibility"},
		{"Normalize by form", `n.Normalize(n.NFC, text)`, "canonical compatibility composition"},
		{"NormalizeWithin by form", `n.NormalizeWithin(n.NFC, text, 10)`, "canonical compatibility composition"},
		{"Lowercase", `n.Lowercase(text)`, "lower"},
		{"LowercaseWithin", `n.LowercaseWithin(text, 10)`, "lower"},
		{"Uppercase", `n.Uppercase(text)`, "upper"},
		{"UppercaseWithin", `n.UppercaseWithin(text, 10)`, "upper"},
		{"nothing", `text, n.NFC.String()`, ""},
	} {
		t.Run(each.name, func(t *testing.T) {
			name := strings.ReplaceAll(each.name, " ", "_")
			write(name+"/main.go", fmt.Sprintf(`package main

import (
	"fmt"
	"os"

	n %q
)

func main() {
	text := os.Args[0]
	fmt.Println(%s)
}
`, modulePath, each.call))
			program := filepath.Join(dir, name+".bin")
			build := exec.Command(goTool, "build", "-o", program, "./"+name)
			build.Dir = dir
			build.Env = append(os.Environ(), "GOWORK=off", "GOFLAGS=-mod=mod")
			if out, err := build.CombinedOutput(); err != nil {
				t.Fatalf("go build: %v\n%s", err, out)
			}
			nm, err := exec.Command(goTool, "tool", "nm", program).Output()
			if err != nil {
				t.Fatalf("go tool nm: %v", err)
			}
			carried := make(map[string]bool)
			for _, line := range strings.Split(string(nm), "\n") {
				if fields := strings.Fields(line); len(fields) >= 3 && strings.HasPrefix(fields[2], modulePath+".") {
					carried[strings.TrimPrefix(fields[2], modulePath+".")] = true
				}
			}
			wanted := " " + each.groups + " "
			for _, group := range tableGroups {
				want := strings.Contains(wanted, " "+group.name+" ")
				for _, symbol := range group.symbols {
					if carried[symbol] != want {
						t.Errorf("the program has %s, %v, and its group %s is wanted: %v", symbol, carried[symbol],
							group.name, want)
					}
				}
			}
		})
	}
}
