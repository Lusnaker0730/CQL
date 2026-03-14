#!/usr/bin/env python3
"""
Generate Monaco Monarch token lists from the official CQL ANTLR grammar.

Reads:  scripts/grammar/cql.g4, scripts/grammar/fhirpath.g4
Writes: src/utils/cqlTokens.generated.ts

Usage:
  cd frontend
  python scripts/generate-monarch-tokens.py          # generate
  python scripts/generate-monarch-tokens.py --check   # CI: diff-check only
"""

import re
import sys
from pathlib import Path

SCRIPT_DIR = Path(__file__).parent
GRAMMAR_DIR = SCRIPT_DIR / "grammar"
OUTPUT_FILE = SCRIPT_DIR.parent / "src" / "utils" / "cqlTokens.generated.ts"

CQL_G4 = GRAMMAR_DIR / "cql.g4"
FHIRPATH_G4 = GRAMMAR_DIR / "fhirpath.g4"


def extract_rule_alternatives(grammar_text: str, rule_name: str) -> list[str]:
    """
    Extract string literal alternatives from a named ANTLR rule.
    e.g. keyword : 'after' | 'aggregate' | ... ;
    Returns sorted unique list of unquoted strings.
    """
    # Match rule: name : ... ;  (handles multi-line)
    pattern = rf"^{re.escape(rule_name)}\s*\n?\s*:(.+?);\s*$"
    match = re.search(pattern, grammar_text, re.MULTILINE | re.DOTALL)
    if not match:
        return []
    body = match.group(1)
    # Extract all single-quoted string literals
    literals = re.findall(r"'([^']+)'", body)
    return sorted(set(literals))


def extract_keywords(cql_text: str) -> list[str]:
    """Extract all keywords from the 'keyword' rule in cql.g4."""
    keywords = extract_rule_alternatives(cql_text, "keyword")
    # Filter out multi-word phrases that can't be single Monarch tokens
    # (e.g. 'such that', 'included in', 'on or', 'or before', etc.)
    single_word = [k for k in keywords if " " not in k]
    return single_word


def extract_multi_word_keywords(cql_text: str) -> list[str]:
    """Extract multi-word keyword phrases from the 'keyword' rule."""
    keywords = extract_rule_alternatives(cql_text, "keyword")
    return [k for k in keywords if " " in k]


def extract_type_keywords(cql_text: str) -> list[str]:
    """
    Extract type-related keywords.
    These are capitalized keywords that act as type constructors:
    Code, Concept, Interval, List, Tuple, Choice
    Plus the CQL built-in type names.
    """
    # From typeNameIdentifier + type constructors used in grammar
    type_names = extract_rule_alternatives(cql_text, "typeNameIdentifier")

    # Add type constructors that appear as literal tokens in type specifiers
    grammar_types = set()
    for kw in ["List", "Interval", "Tuple", "Choice", "Concept", "Code"]:
        if f"'{kw}'" in cql_text:
            grammar_types.add(kw)

    # CQL built-in primitive types (not in grammar as keywords, but as named types)
    builtin_types = [
        "Boolean", "Integer", "Decimal", "String",
        "Date", "DateTime", "Time",
        "Quantity", "Ratio", "Any",
    ]

    all_types = sorted(set(type_names) | grammar_types | set(builtin_types))
    return all_types


def extract_datetime_precisions(cql_text: str, fhirpath_text: str) -> list[str]:
    """Extract dateTimePrecision and pluralDateTimePrecision tokens."""
    singular = extract_rule_alternatives(cql_text, "dateTimePrecision")
    if not singular:
        singular = extract_rule_alternatives(fhirpath_text, "dateTimePrecision")
    plural = extract_rule_alternatives(cql_text, "pluralDateTimePrecision")
    if not plural:
        plural = extract_rule_alternatives(fhirpath_text, "pluralDateTimePrecision")
    return sorted(set(singular + plural))


def extract_reserved_words(cql_text: str) -> list[str]:
    """Extract reserved words that cannot be used as identifiers."""
    reserved = extract_rule_alternatives(cql_text, "reservedWord")
    return [r for r in reserved if " " not in r]


def generate_typescript(
    keywords: list[str],
    multi_word_keywords: list[str],
    type_keywords: list[str],
    datetime_precisions: list[str],
    reserved_words: list[str],
    grammar_version: str,
) -> str:
    """Generate TypeScript source with token arrays."""

    def format_array(items: list[str], indent: str = "  ") -> str:
        lines = [f"{indent}'{item}'," for item in items]
        return "\n".join(lines)

    return f"""\
/**
 * CQL Monarch Token Definitions
 *
 * AUTO-GENERATED from official CQL ANTLR grammar ({grammar_version}).
 * Source: https://github.com/cqframework/clinical_quality_language
 * Grammar files: scripts/grammar/cql.g4, scripts/grammar/fhirpath.g4
 *
 * DO NOT EDIT MANUALLY.
 * Regenerate: python scripts/generate-monarch-tokens.py
 */

/**
 * All single-word CQL keywords from the `keyword` rule in cql.g4.
 * Used by Monarch tokenizer for syntax highlighting.
 */
export const CQL_KEYWORDS: readonly string[] = [
{format_array(keywords)}
] as const

/**
 * Multi-word CQL keyword phrases from the `keyword` rule.
 * These require special Monarch rules (regex with spaces).
 */
export const CQL_MULTI_WORD_KEYWORDS: readonly string[] = [
{format_array(multi_word_keywords)}
] as const

/**
 * CQL type keywords — built-in types and type constructors.
 * Highlighted differently from regular keywords.
 */
export const CQL_TYPE_KEYWORDS: readonly string[] = [
{format_array(type_keywords)}
] as const

/**
 * Date/time precision keywords (singular + plural forms).
 * Subset of keywords, useful for specialized highlighting or validation.
 */
export const CQL_DATETIME_PRECISIONS: readonly string[] = [
{format_array(datetime_precisions)}
] as const

/**
 * Reserved words that may NOT be used as identifiers.
 * From the `reservedWord` rule in cql.g4.
 */
export const CQL_RESERVED_WORDS: readonly string[] = [
{format_array(reserved_words)}
] as const

/**
 * Grammar metadata for drift detection.
 */
export const CQL_GRAMMAR_META = {{
  version: '{grammar_version}',
  generatedAt: '{__import_datetime()}',
  keywordCount: {len(keywords)},
  typeKeywordCount: {len(type_keywords)},
  reservedWordCount: {len(reserved_words)},
}} as const
"""


def __import_datetime() -> str:
    from datetime import datetime, timezone
    return datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def extract_grammar_version(cql_text: str) -> str:
    """Extract version from grammar comment header."""
    match = re.search(r"Version\s+([\d.]+)", cql_text)
    return match.group(1) if match else "unknown"


def main():
    check_mode = "--check" in sys.argv

    if not CQL_G4.exists():
        print(f"ERROR: Grammar file not found: {CQL_G4}")
        print("Run: fetch the grammar from https://github.com/cqframework/clinical_quality_language")
        sys.exit(1)

    cql_text = CQL_G4.read_text(encoding="utf-8")
    fhirpath_text = FHIRPATH_G4.read_text(encoding="utf-8") if FHIRPATH_G4.exists() else ""

    grammar_version = extract_grammar_version(cql_text)
    keywords = extract_keywords(cql_text)
    multi_word = extract_multi_word_keywords(cql_text)
    type_keywords = extract_type_keywords(cql_text)
    dt_precisions = extract_datetime_precisions(cql_text, fhirpath_text)
    reserved = extract_reserved_words(cql_text)

    print(f"CQL Grammar Version: {grammar_version}")
    print(f"  Keywords (single-word): {len(keywords)}")
    print(f"  Keywords (multi-word):  {len(multi_word)}")
    print(f"  Type keywords:          {len(type_keywords)}")
    print(f"  DateTime precisions:    {len(dt_precisions)}")
    print(f"  Reserved words:         {len(reserved)}")

    output = generate_typescript(
        keywords, multi_word, type_keywords, dt_precisions, reserved, grammar_version
    )

    if check_mode:
        if not OUTPUT_FILE.exists():
            print(f"ERROR: Generated file does not exist: {OUTPUT_FILE}")
            print("Run: python scripts/generate-monarch-tokens.py")
            sys.exit(1)

        existing = OUTPUT_FILE.read_text(encoding="utf-8")
        # Compare ignoring generatedAt timestamp
        def strip_timestamp(s: str) -> str:
            return re.sub(r"generatedAt: '[^']+'", "generatedAt: '<timestamp>'", s)

        if strip_timestamp(existing) != strip_timestamp(output):
            print("ERROR: cqlTokens.generated.ts is out of date!")
            print("Run: python scripts/generate-monarch-tokens.py")
            sys.exit(1)
        else:
            print("OK: cqlTokens.generated.ts is up to date")
            sys.exit(0)
    else:
        OUTPUT_FILE.write_text(output, encoding="utf-8")
        print(f"\nGenerated: {OUTPUT_FILE}")


if __name__ == "__main__":
    main()
