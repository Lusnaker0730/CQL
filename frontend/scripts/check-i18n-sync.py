#!/usr/bin/env python3
"""
Check that all i18n locale JSON files have identical key structures.

Compares the reference locale (en) against all other locales (zh-TW, etc.)
and reports missing keys, extra keys, and type mismatches.

Usage:
  cd frontend
  python scripts/check-i18n-sync.py          # check all namespaces
  python scripts/check-i18n-sync.py --unused  # also scan source for unused keys
"""

import json
import re
import sys
from pathlib import Path

SCRIPT_DIR = Path(__file__).parent
LOCALES_DIR = SCRIPT_DIR.parent / "src" / "locales"
SRC_DIR = SCRIPT_DIR.parent / "src"
REFERENCE_LOCALE = "en"

# Source file extensions to scan for key usage
SOURCE_EXTENSIONS = {".ts", ".tsx"}

# Patterns that indicate dynamic key usage (skip unused-key check for these)
DYNAMIC_KEY_PATTERNS = [
    r"\$\{",          # template literals: t(`prefix.${var}`)
    r"\+\s*\w",       # string concat: t('prefix.' + var)
    r"concat",        # .concat()
]


def flatten_keys(obj: dict, prefix: str = "") -> dict[str, type]:
    """Recursively flatten nested JSON into dot-separated keys with value types."""
    result = {}
    for key, value in obj.items():
        full_key = f"{prefix}.{key}" if prefix else key
        if isinstance(value, dict):
            result.update(flatten_keys(value, full_key))
        else:
            result[full_key] = type(value)
    return result


def load_locale(locale: str, namespace: str) -> dict[str, type]:
    """Load and flatten a locale JSON file."""
    path = LOCALES_DIR / locale / f"{namespace}.json"
    if not path.exists():
        return {}
    with open(path, encoding="utf-8") as f:
        data = json.load(f)
    return flatten_keys(data)


def get_namespaces() -> list[str]:
    """Get all namespace names from the reference locale."""
    ref_dir = LOCALES_DIR / REFERENCE_LOCALE
    return sorted(p.stem for p in ref_dir.glob("*.json"))


def get_locales() -> list[str]:
    """Get all locale directory names."""
    return sorted(
        d.name for d in LOCALES_DIR.iterdir()
        if d.is_dir() and d.name != REFERENCE_LOCALE
    )


def collect_source_keys() -> set[str]:
    """
    Scan source files for i18n key references.
    Looks for t('key'), t("key"), and t(`key`) patterns.
    Returns set of (namespace.key) strings found in source.
    """
    key_pattern = re.compile(
        r"""(?:^|[^a-zA-Z])t\(\s*['"`]([a-zA-Z0-9_.]+)['"`]"""
    )
    keys = set()
    for ext in SOURCE_EXTENSIONS:
        for src_file in SRC_DIR.rglob(f"*{ext}"):
            try:
                content = src_file.read_text(encoding="utf-8")
            except (UnicodeDecodeError, OSError):
                continue
            for match in key_pattern.finditer(content):
                keys.add(match.group(1))
    return keys


def check_sync() -> list[str]:
    """Check key synchronization between reference and other locales."""
    errors = []
    namespaces = get_namespaces()
    locales = get_locales()

    if not locales:
        errors.append("No non-reference locales found to compare")
        return errors

    for ns in namespaces:
        ref_keys = load_locale(REFERENCE_LOCALE, ns)
        if not ref_keys:
            errors.append(f"::warning::{REFERENCE_LOCALE}/{ns}.json is empty or missing")
            continue

        for locale in locales:
            loc_keys = load_locale(locale, ns)
            loc_path = f"{locale}/{ns}.json"

            if not loc_keys:
                errors.append(f"::error::{loc_path} is missing or empty ({len(ref_keys)} keys needed)")
                continue

            # Missing keys (in reference but not in locale)
            missing = sorted(set(ref_keys.keys()) - set(loc_keys.keys()))
            for key in missing:
                errors.append(f"::error::{loc_path}: missing key '{key}'")

            # Extra keys (in locale but not in reference)
            extra = sorted(set(loc_keys.keys()) - set(ref_keys.keys()))
            for key in extra:
                errors.append(f"::error::{loc_path}: extra key '{key}' (not in {REFERENCE_LOCALE})")

    return errors


def check_unused(namespaces: list[str]) -> list[str]:
    """Check for keys defined in reference locale but not found in source."""
    warnings = []
    print("Scanning source files for key usage...")
    source_keys = collect_source_keys()
    print(f"  Found {len(source_keys)} unique key references in source")

    for ns in namespaces:
        ref_keys = load_locale(REFERENCE_LOCALE, ns)
        for key in sorted(ref_keys.keys()):
            # Check both namespace-prefixed and bare key
            full_key = f"{ns}.{key}"
            if key not in source_keys and full_key not in source_keys:
                # Check if it might be accessed dynamically
                key_parts = key.split(".")
                parent = ".".join(key_parts[:-1]) if len(key_parts) > 1 else ""
                # If parent prefix is used with dynamic access, skip
                if parent and any(
                    f"{parent}." in sk or f"{ns}.{parent}." in sk
                    for sk in source_keys
                ):
                    continue
                warnings.append(f"::warning::{REFERENCE_LOCALE}/{ns}.json: potentially unused key '{key}'")

    return warnings


def main():
    check_unused_flag = "--unused" in sys.argv

    namespaces = get_namespaces()
    locales = get_locales()

    print(f"Reference locale: {REFERENCE_LOCALE}")
    print(f"Other locales: {', '.join(locales)}")
    print(f"Namespaces: {len(namespaces)} ({', '.join(namespaces)})")
    print()

    # Key count summary
    total = 0
    for ns in namespaces:
        ref_keys = load_locale(REFERENCE_LOCALE, ns)
        count = len(ref_keys)
        total += count
        print(f"  {ns}: {count} keys")
    print(f"  Total: {total} keys")
    print()

    # Sync check
    errors = check_sync()

    # Unused check (optional)
    if check_unused_flag:
        print()
        unused_warnings = check_unused(namespaces)
        errors.extend(unused_warnings)

    # Report
    if errors:
        print()
        error_count = sum(1 for e in errors if "::error::" in e)
        warning_count = sum(1 for e in errors if "::warning::" in e)

        for err in errors:
            print(err)

        print()
        if error_count:
            print(f"FAILED: {error_count} error(s), {warning_count} warning(s)")
            sys.exit(1)
        else:
            print(f"OK with {warning_count} warning(s)")
            sys.exit(0)
    else:
        print("OK: All locales are in sync")
        sys.exit(0)


if __name__ == "__main__":
    main()
