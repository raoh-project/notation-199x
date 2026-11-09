package net.unit8.notation199x;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Holds what a program loads of the library's tables to what it asks of the library: a program of
 * one feature loads the tables of the groups that feature needs, and of no other. A program of NFC or
 * NFD does not load the compatibility mappings, one of NFD or NFKD does not load the composition
 * tables, one that lowercases does not load the uppercase tables and one that uppercases does not
 * load the lowercase ones, and one that asks nothing of the tables loads none.
 *
 * <p>Each program is a JVM of its own that does one thing, and what it loaded is read off its class
 * loading log. The needed groups are found loaded too, which keeps the check from passing on a log
 * it cannot read.
 */
class AProgramLoadsOnlyTheTablesItsFeatureNeedsTest {

    /** The classes of each group of tables, by the group's name. */
    private static final Map<String, List<String>> GROUPS = Map.of(
            "canonical", List.of("NormalizationTables"),
            "compatibility", List.of("NormalizationTables$Compatibility"),
            "composition", List.of("NormalizationTables$Composition"),
            "lower", List.of("CaseTables$Lower", "CaseConversion$Lower"),
            "upper", List.of("CaseTables$Upper", "CaseConversion$Upper"));

    @Test
    void nfcLoadsTheCanonicalAndCompositionTables() throws Exception {
        loads("nfc", "canonical", "composition");
    }

    @Test
    void nfdLoadsTheCanonicalTables() throws Exception {
        loads("nfd", "canonical");
    }

    @Test
    void nfkcLoadsTheCanonicalCompatibilityAndCompositionTables() throws Exception {
        loads("nfkc", "canonical", "compatibility", "composition");
    }

    @Test
    void nfkdLoadsTheCanonicalAndCompatibilityTables() throws Exception {
        loads("nfkd", "canonical", "compatibility");
    }

    @Test
    void lowercaseLoadsTheLowerTables() throws Exception {
        loads("lowercase", "lower");
    }

    @Test
    void uppercaseLoadsTheUpperTables() throws Exception {
        loads("uppercase", "upper");
    }

    @Test
    void textBelowTheTrivialLimitLoadsNoTable() throws Exception {
        loads("ascii");
    }

    @Test
    void askingNothingLoadsNoTable() throws Exception {
        loads("nothing");
    }

    /** Runs the program of {@code scenario} and holds the groups it loaded to {@code needed}. */
    private static void loads(String scenario, String... needed) throws IOException, InterruptedException,
            URISyntaxException {
        Set<String> loaded = classesLoadedBy(scenario);
        assertTrue(loaded.contains(Program.class.getName()), "the log shows the program " + scenario + " loaded");
        Set<String> wanted = new TreeSet<>(List.of(needed));
        List<String> wrong = new ArrayList<>();
        for (Map.Entry<String, List<String>> group : GROUPS.entrySet()) {
            for (String name : group.getValue()) {
                boolean has = loaded.contains(Program.class.getPackageName() + "." + name);
                if (has != wanted.contains(group.getKey())) {
                    wrong.add(name + (has ? " is loaded" : " is not loaded") + ", of group " + group.getKey());
                }
            }
        }
        assertEquals(List.of(), wrong, "the program of " + scenario + " loads the groups " + wanted);
    }

    /** The classes of this library that a JVM running {@link Program} on {@code scenario} loads. */
    private static Set<String> classesLoadedBy(String scenario) throws IOException, InterruptedException,
            URISyntaxException {
        Path log = Files.createTempFile("class-load", ".log");
        try {
            String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
            String classPath = codeSource(Normalization.class) + System.getProperty("path.separator")
                    + codeSource(Program.class);
            Process process = new ProcessBuilder(java, "-Xshare:auto", "-Xlog:class+load=info:file=" + log + ":none",
                    "-cp", classPath, Program.class.getName(), scenario)
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
            assertEquals(0, process.waitFor(), "the program of " + scenario + " ends");
            return Files.readAllLines(log).stream()
                    .map(line -> line.split(" ", 2)[0])
                    .collect(Collectors.toSet());
        } finally {
            Files.deleteIfExists(log);
        }
    }

    private static Path codeSource(Class<?> type) throws URISyntaxException {
        return Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI());
    }

    /** The programs: each does the one thing its name says, with text that needs what it is about. */
    public static final class Program {

        private Program() {}

        /**
         * Does what {@code args[0]} names.
         *
         * @param args the scenario
         */
        public static void main(String[] args) {
            String answer = switch (args[0]) {
                // A starter and a mark that composes with it: decomposed already, so the forms
                // that compose have something to do.
                case "nfc" -> Normalization.nfc("é");
                case "nfd" -> Normalization.normalize(Normalization.Form.NFD, "é");
                // U+FB01, a compatibility ligature, and a mark after it.
                case "nfkc" -> Normalization.normalize(Normalization.Form.NFKC, "ﬁ́");
                case "nfkd" -> Normalization.normalize(Normalization.Form.NFKD, "ﬁ");
                case "lowercase" -> CaseConversion.lowercase("ÉΣ");
                case "uppercase" -> CaseConversion.uppercase("éß");
                case "ascii" -> Normalization.nfc("ASCII text");
                case "nothing" -> "";
                default -> throw new IllegalArgumentException(args[0]);
            };
            if (answer == null) {
                throw new IllegalStateException();
            }
        }
    }
}
