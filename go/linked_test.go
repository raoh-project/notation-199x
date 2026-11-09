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
	// Every go the test runs is run as the others are, in a module of its own and not in a workspace
	// that holds the library or in what the caller's GOFLAGS say: a workspace of two modules makes
	// go list -m answer both, and the module this builds is the one the temporary go.mod names.
	goIn := func(dir string, args ...string) *exec.Cmd {
		command := exec.Command(goTool, args...)
		command.Dir = dir
		command.Env = append(os.Environ(), "GOWORK=off", "GOFLAGS=-mod=mod")
		return command
	}
	module, err := filepath.Abs(".")
	if err != nil {
		t.Fatal(err)
	}
	// The path of this module and its Go are what its go.mod says, which the programs the test
	// builds import and are of, and the symbols of what they carry are named by. A name written out
	// here would be one go.mod can leave behind, and go builds a program that replaces a module by one
	// of another name, so the test would go on passing on a module that is not this one.
	listed, err := goIn(module, "list", "-m", "-f", "{{.Path}} {{.GoVersion}}").Output()
	if err != nil {
		t.Fatalf("go list -m: %v", err)
	}
	fields := strings.Fields(string(listed))
	if len(fields) != 2 {
		t.Fatalf("go list -m answers %q, which is not a module path and a Go version", listed)
	}
	modulePath, goVersion := fields[0], fields[1]
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
	write("go.mod", "module linked\n\ngo "+goVersion+"\n")
	edit := goIn(dir, "mod", "edit", "-require="+modulePath+"@v0.0.0", "-replace="+modulePath+"="+module)
	if out, err := edit.CombinedOutput(); err != nil {
		t.Fatalf("go mod edit: %v\n%s", err, out)
	}

	for _, each := range []struct {
		name string
		// call is what the program does with its argument, text, and the groups that needs.
		call   string
		groups string
	}{
		{"NFC", `n.NormalizeNFC(text)`, "canonical composition"},
		{"NFC within", `n.NormalizeNFCWithin(text, 10)`, "canonical composition"},
		{"NFD", `n.NormalizeNFD(text)`, "canonical"},
		{"NFD within", `n.NormalizeNFDWithin(text, 10)`, "canonical"},
		{"NFKC", `n.NormalizeNFKC(text)`, "canonical compatibility composition"},
		{"NFKC within", `n.NormalizeNFKCWithin(text, 10)`, "canonical compatibility composition"},
		{"NFKD", `n.NormalizeNFKD(text)`, "canonical compatibility"},
		{"NFKD within", `n.NormalizeNFKDWithin(text, 10)`, "canonical compatibility"},
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
			build := goIn(dir, "build", "-o", program, "./"+name)
			if out, err := build.CombinedOutput(); err != nil {
				t.Fatalf("go build: %v\n%s", err, out)
			}
			nm, err := goIn(dir, "tool", "nm", program).Output()
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
