#!/usr/bin/env python3
"""
Remove all Java comments (// single-line, /* */ block, /** */ Javadoc) from .java files
while correctly preserving comments inside string literals.
"""

import os
import re
import sys


def remove_java_comments(source: str) -> str:
    """
    State-machine parser that strips Java comments while leaving string contents intact.

    States:
      NORMAL          - regular code
      LINE_COMMENT    - inside a // comment (ends at newline)
      BLOCK_COMMENT   - inside a /* ... */ or /** ... */ comment
      STRING          - inside a "..." string literal
      CHAR            - inside a '.' character literal
    """
    result = []
    i = 0
    n = len(source)

    while i < n:
        c = source[i]

        # ── String literal ────────────────────────────────────────────────────
        if c == '"':
            # Check for text block (triple-quote) introduced in Java 13+
            if source[i:i+3] == '"""':
                result.append('"""')
                i += 3
                # Consume until the closing """
                while i < n:
                    if source[i:i+3] == '"""':
                        result.append('"""')
                        i += 3
                        break
                    result.append(source[i])
                    i += 1
            else:
                # Regular string literal
                result.append(c)
                i += 1
                while i < n:
                    ch = source[i]
                    result.append(ch)
                    i += 1
                    if ch == '\\':
                        # Escape sequence – copy next char verbatim
                        if i < n:
                            result.append(source[i])
                            i += 1
                    elif ch == '"':
                        break

        # ── Character literal ─────────────────────────────────────────────────
        elif c == "'":
            result.append(c)
            i += 1
            while i < n:
                ch = source[i]
                result.append(ch)
                i += 1
                if ch == '\\':
                    if i < n:
                        result.append(source[i])
                        i += 1
                elif ch == "'":
                    break

        # ── Potential comment start ───────────────────────────────────────────
        elif c == '/' and i + 1 < n:
            next_c = source[i + 1]

            if next_c == '/':
                # Single-line comment: skip until end of line (keep the newline)
                i += 2
                while i < n and source[i] != '\n':
                    i += 1
                # Leave the newline to be appended in the next iteration

            elif next_c == '*':
                # Block / Javadoc comment: skip until */
                i += 2
                while i < n:
                    if source[i] == '*' and i + 1 < n and source[i + 1] == '/':
                        i += 2
                        break
                    i += 1
                # Optionally replace the comment region with a single space
                # so tokens on either side don't accidentally merge.
                # (For Java the newline approach is fine; we do nothing extra.)

            else:
                result.append(c)
                i += 1

        else:
            result.append(c)
            i += 1

    return ''.join(result)


def collapse_blank_lines(text: str) -> str:
    """
    Collapse runs of more than one consecutive blank line into a single blank line.
    Also strips trailing whitespace on each line.
    """
    lines = text.splitlines()
    # Strip trailing whitespace from every line
    lines = [line.rstrip() for line in lines]

    result_lines = []
    blank_run = 0
    for line in lines:
        if line == '':
            blank_run += 1
            if blank_run <= 1:
                result_lines.append(line)
        else:
            blank_run = 0
            result_lines.append(line)

    # Ensure the file ends with a single newline
    text = '\n'.join(result_lines)
    text = text.rstrip('\n') + '\n'
    return text


def process_file(path: str) -> bool:
    """Read, strip comments, collapse blanks, write back. Returns True if changed."""
    with open(path, 'r', encoding='utf-8', errors='replace') as fh:
        original = fh.read()

    no_comments = remove_java_comments(original)
    cleaned = collapse_blank_lines(no_comments)

    if cleaned != original:
        with open(path, 'w', encoding='utf-8') as fh:
            fh.write(cleaned)
        return True
    return False


def main():
    root = '/Users/ayomidefalade/Downloads/MerchantWallet/src'
    java_files = []
    for dirpath, _, filenames in os.walk(root):
        for fname in filenames:
            if fname.endswith('.java'):
                java_files.append(os.path.join(dirpath, fname))

    java_files.sort()
    changed = 0
    unchanged = 0
    errors = 0

    for path in java_files:
        try:
            was_changed = process_file(path)
            if was_changed:
                changed += 1
                print(f"  CLEANED  {path}")
            else:
                unchanged += 1
                print(f"  no-op    {path}")
        except Exception as exc:
            errors += 1
            print(f"  ERROR    {path}: {exc}", file=sys.stderr)

    print(f"\nDone. {len(java_files)} files processed: "
          f"{changed} cleaned, {unchanged} unchanged, {errors} errors.")


if __name__ == '__main__':
    main()
