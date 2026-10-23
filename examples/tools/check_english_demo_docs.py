#!/usr/bin/env python3
"""Reject Chinese text in public demo documentation before publication."""
import argparse
from pathlib import Path
import re


CHINESE = re.compile(r'[\u3400-\u4dbf\u4e00-\u9fff\uf900-\ufaff]')


def violations(root):
    root = Path(root)
    failures = []
    for document in sorted(root.glob('demos/**/*.md')):
        if any(part in {'target', 'node_modules', '.git'} for part in document.relative_to(root).parts):
            continue
        for number, line in enumerate(document.read_text(encoding='utf-8').splitlines(), 1):
            if CHINESE.search(line):
                failures.append(f'{document.relative_to(root)}:{number}: English translation required')
                break
    return failures


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path, default=Path(__file__).resolve().parent.parent)
    arguments = parser.parse_args()
    failures = violations(arguments.root)
    if failures:
        print('\n'.join(failures))
        print(f'Publication blocked: {len(failures)} demo documents require translation.')
        return 1
    print('Public demo Markdown, including README and contribution rules, contains no Chinese text.')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
