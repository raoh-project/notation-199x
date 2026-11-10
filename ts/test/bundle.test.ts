// What a bundle of a program carries of the package's tables is held to what the program asks of
// the package: a program of NFC or NFD does not carry the compatibility mappings, one of NFD or
// NFKD does not carry the compositions, one that lowercases does not carry the uppercase tables
// and one that uppercases does not carry the lowercase tables, and one that asks for none of
// them carries no table. The Rust crate's linked/check.sh holds the same of a linked program.
//
// Each table is looked for in the bundle by a sample of its own content, taken from the module that
// declares it and printed the way the bundler prints it. A table that is needed is looked for as
// well, so that a bundle whose tables are written some other way is found out and not passed.

import assert from "node:assert/strict";
import { it } from "node:test";
import { fileURLToPath } from "node:url";
import { build, transformSync } from "esbuild";
import { CANONICAL_TO, COMBINING_CLASS_PAGES, DECOMPOSITION_POSITION_PAGES, STABLE_PAGES } from "../src/normalization_tables.ts";
import { COMPATIBILITY_TO } from "../src/normalization_compatibility_tables.ts";
import { COMPOSITION_FIRST_PAGES, COMPOSITION_PAIRS } from "../src/normalization_composition_tables.ts";
import { CASE_CONTEXT_PAGES, LOWER_POSITION_PAGES, LOWER_TO } from "../src/case_lower_tables.ts";
import { UPPER_POSITION_PAGES, UPPER_TO } from "../src/case_upper_tables.ts";

// The tables of each group, by the name each is declared under.
const GROUPS: Readonly<Record<string, Readonly<Record<string, string | readonly (string | number)[]>>>> = {
  canonical: {
    CANONICAL_TO,
    DECOMPOSITION_POSITION_PAGES,
    COMBINING_CLASS_PAGES,
    STABLE_PAGES,
  },
  compatibility: { COMPATIBILITY_TO },
  composition: { COMPOSITION_FIRST_PAGES, COMPOSITION_PAIRS },
  lower: { LOWER_TO, LOWER_POSITION_PAGES, CASE_CONTEXT_PAGES },
  upper: { UPPER_TO, UPPER_POSITION_PAGES },
};

// What a program that asks for each of the package's functions carries.
const SCENARIOS: readonly { readonly name: string; readonly program: string; readonly carries: readonly string[] }[] = [
  ...["NFC", "NFD", "NFKC", "NFKD"].flatMap((form) => {
    const groups = ["canonical", ...form.includes("K") ? ["compatibility"] : [], ...form.endsWith("C") ? ["composition"] : []];
    return [
      { name: `normalize${form}`, program: `normalize${form}("x")`, carries: groups },
      { name: `normalize${form}Within`, program: `normalize${form}Within("x", 1)`, carries: groups },
    ];
  }),
  { name: "normalize(form, ...)", program: `normalize(form, "x")`, carries: ["canonical", "compatibility", "composition"] },
  { name: "normalizeWithin(form, ...)", program: `normalizeWithin(form, "x", 1)`,
    carries: ["canonical", "compatibility", "composition"] },
  { name: "lowercase", program: `lowercase("x")`, carries: ["lower"] },
  { name: "lowercaseWithin", program: `lowercaseWithin("x", 1)`, carries: ["lower"] },
  { name: "uppercase", program: `uppercase("x")`, carries: ["upper"] },
  { name: "uppercaseWithin", program: `uppercaseWithin("x", 1)`, carries: ["upper"] },
  { name: "nothing of the tables", program: `scalarCount("x")`, carries: [] },
];

// The sources are TypeScript where the tests run as it, and JavaScript where they were compiled.
const EXTENSION = import.meta.url.endsWith(".ts") ? "ts" : "js";
const SOURCES = fileURLToPath(new URL("../src/", import.meta.url));

/** The program, bundled and minified, with the package's functions as a program imports them. */
async function bundled(program: string): Promise<string> {
  const names = [...program.matchAll(/\b[a-z][A-Za-z]*(?=\()/g)].map((each) => each[0]);
  const entry = `import { ${[...new Set(names)].join(", ")} } from "./index.${EXTENSION}";\n`
    // An assignment is kept, and so is what it reads; the program is only to reach the functions.
    + `declare const form: any; globalThis.kept = () => ${program};\n`;
  const result = await build({
    stdin: { contents: entry, resolveDir: SOURCES, loader: "ts" },
    bundle: true,
    minify: true,
    write: false,
    format: "esm",
    platform: "neutral",
    target: "es2022",
    logLevel: "silent",
  });
  return result.outputFiles[0]!.text;
}

/** `value` printed by esbuild, without what encloses it: a string without its quotes, and an array
 *  without its brackets. */
function printed(value: string | readonly (string | number)[]): string {
  const code = transformSync(`x=${JSON.stringify(value)}`, { minify: true }).code.trimEnd().replace(/;$/, "");
  return code.slice(code.indexOf("=") + 2, -1);
}

type Table = string | readonly (string | number)[];

/** `run`, written as one string, to be looked for in another table's values. */
function flat(run: Table): string {
  return typeof run === "string" ? run : run.join(",");
}

/**
 * What distinguishes `table` among `tables`: a run of its values that is in none of the others,
 * and that a printer prints the same wherever it is, the one with the most different values and,
 * of those, the first from the middle on.
 */
function sample(name: string, table: Table, tables: readonly Table[]): string {
  const width = typeof table === "string" ? 32 : 6;
  const middle = Math.floor(table.length / 2);
  const starts = Array.from({ length: table.length - width + 1 }, (_, at) => (middle + at) % (table.length - width + 1));
  const candidates: { run: Table; different: number }[] = [];
  for (const from of starts) {
    const run = table.slice(from, from + width);
    // A quote, a backslash or a line break in a string is written as the printer chooses for the
    // whole string and not for the run, and so is a NUL as the last of it, by what follows.
    const plain = typeof run !== "string" || ![...run].some((unit) => "\"'`\\$\n\r".includes(unit))
      && !run.endsWith("\0");
    if (plain) {
      candidates.push({ run, different: new Set(typeof run === "string" ? [...run] : run).size });
    }
  }
  // Array.prototype.sort is stable, so the first from the middle on is the first of as many.
  candidates.sort((a, b) => b.different - a.different);
  for (const { run } of candidates) {
    if (tables.every((other) => other === table || typeof other !== typeof run || !flat(other).includes(flat(run)))) {
      return printed(run);
    }
  }
  throw new Error(`${name} has no run to tell it by`);
}

const ALL = Object.values(GROUPS).flatMap((tables) => Object.values(tables));
const SAMPLES = Object.entries(GROUPS).flatMap(([group, tables]) =>
  Object.entries(tables).map(([name, table]) => ({ group, name, text: sample(name, table, ALL) })));

function occurrences(text: string, part: string): number {
  return text.split(part).length - 1;
}

for (const { name, program, carries } of SCENARIOS) {
  it(`a program of ${name} carries the tables of ${carries.join(", ") || "no group"} and no other`, async () => {
    const code = await bundled(program);
    for (const { group, name: table, text } of SAMPLES) {
      const found = occurrences(code, text);
      assert.equal(found, carries.includes(group) ? 1 : 0,
        `${table} of ${group} is in the bundle ${found} times, where the program is of ${name}`);
    }
  });
}

// What a bundle's size is held to: 228,888 bytes of NFC and 91,009 of lowercase when measured, with
// esbuild 0.28.2, and a margin of about 15% over each. A table or a loop that a program did not carry
// before is found out, and a change of the bundler's does not fail the test for a few bytes.
const LIMITS: readonly { readonly name: string; readonly program: string; readonly most: number }[] = [
  { name: "NFC", program: `normalizeNFC("x")`, most: 263_000 },
  { name: "lowercase", program: `lowercase("x")`, most: 105_000 },
];

for (const { name, program, most } of LIMITS) {
  it(`a program of ${name} alone is no more than ${most} bytes minified`, async () => {
    const size = Buffer.byteLength(await bundled(program));
    assert.ok(size <= most, `the bundle is ${size} bytes`);
  });
}
