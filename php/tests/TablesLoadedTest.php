<?php

declare(strict_types=1);

namespace Raoh\Notation199x\Tests;

use PHPUnit\Framework\Attributes\DataProvider;
use PHPUnit\Framework\TestCase;

/**
 * A request is compiled and holds the tables it reads and no others: each group of the generated
 * tables is a class of its own, which PHP loads where code that needs it runs. Each scenario runs
 * in a process of its own, as a request does, and asks which of the classes it declared.
 */
final class TablesLoadedTest extends TestCase
{
    private const NAMESPACE = 'Raoh\\Notation199x\\Internal\\';

    private const TABLES = [
        'NormalizationTables',
        'CompatibilityTables',
        'CompositionTables',
        'LowerCaseTables',
        'UpperCaseTables',
    ];

    /**
     * Each scenario as what it does, text chosen so that it reaches every table its form reads: a
     * decomposition, a mark that composes, a compatibility mapping, Hangul, a sigma at the end of
     * a word.
     *
     * @return array<string, array{string, list<string>}>
     */
    public static function scenarios(): array
    {
        $text = "'e\u{0301} \u{212B} \u{FB01} \u{FF71} \u{D55C} \u{1100}\u{1161} \u{30AC} \u{0391}\u{03A3} straße'";
        return [
            'nfc' => ["Normalization::normalize(NormalizationForm::NFC, $text)", ['NormalizationTables', 'CompositionTables']],
            'nfd' => ["Normalization::normalize(NormalizationForm::NFD, $text)", ['NormalizationTables']],
            'nfkc' => [
                "Normalization::normalize(NormalizationForm::NFKC, $text)",
                ['NormalizationTables', 'CompatibilityTables', 'CompositionTables'],
            ],
            'nfkd' => [
                "Normalization::normalize(NormalizationForm::NFKD, $text)",
                ['NormalizationTables', 'CompatibilityTables'],
            ],
            'lowercase' => ["CaseConversion::lowercase($text)", ['LowerCaseTables']],
            'uppercase' => ["CaseConversion::uppercase($text)", ['UpperCaseTables']],
            'nothing' => ['null', []],
        ];
    }

    /**
     * @param list<string> $needed the table classes the scenario reads
     */
    #[DataProvider('scenarios')]
    public function testARequestLoadsTheTablesItReadsAndNoOthers(string $code, array $needed): void
    {
        $script = 'require ' . var_export(dirname(__DIR__) . '/vendor/autoload.php', true) . ';'
            . 'use Raoh\\Notation199x\\CaseConversion; use Raoh\\Notation199x\\Normalization;'
            . 'use Raoh\\Notation199x\\NormalizationForm;'
            . "$code;"
            . '$loaded = []; foreach (' . var_export(self::TABLES, true) . ' as $c) {'
            . ' if (class_exists(' . var_export(self::NAMESPACE, true) . ' . $c, false)) { $loaded[] = $c; } }'
            . 'echo json_encode($loaded);';
        $process = proc_open([PHP_BINARY, '-n', '-r', $script], [1 => ['pipe', 'w'], 2 => ['pipe', 'w']], $pipes);
        self::assertIsResource($process);
        $out = stream_get_contents($pipes[1]);
        $err = stream_get_contents($pipes[2]);
        proc_close($process);
        self::assertSame('', $err);
        self::assertIsString($out);
        $loaded = json_decode($out, true);
        self::assertIsArray($loaded);
        sort($needed);
        sort($loaded);
        self::assertSame($needed, $loaded);
    }
}
