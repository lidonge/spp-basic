#!/usr/bin/env python3
"""Validate the reviewed examples import before publishing the basic source tree."""
import argparse
import hashlib
import json
from pathlib import Path, PurePosixPath
import re
from urllib.parse import unquote, urlsplit


IGNORED = {'target', '__pycache__', 'node_modules', '.venv'}
CHINESE = re.compile(r'[\u3400-\u4dbf\u4e00-\u9fff\uf900-\ufaff]')
SECRET = re.compile(rb'-----BEGIN (?:RSA |EC |OPENSSH |ENCRYPTED )?PRIVATE KEY-----'
                    rb'|\bgh[pousr]_[A-Za-z0-9]{30,}\b')


def validate(root):
    root = Path(root).resolve()
    manifest = json.loads((root / 'IMPORT.json').read_text())
    errors = []
    expected = {'IMPORT.json'}
    for entry in manifest['files']:
        relative = PurePosixPath(entry['path'])
        if relative.is_absolute() or '..' in relative.parts or '\\' in entry['path']:
            errors.append(f'Unsafe import path: {relative}')
            continue
        if entry['path'] in expected:
            errors.append(f'Duplicate import path: {relative}')
        expected.add(entry['path'])
        source = root / relative
        if source.is_symlink() or not source.is_file():
            errors.append(f'Missing or indirect import: {relative}')
            continue
        data = source.read_bytes()
        if hashlib.sha256(data).hexdigest() != entry['importedSha256']:
            errors.append(f'Unreviewed import change: {relative}')
        if bool(source.stat().st_mode & 0o111) != entry['executable']:
            errors.append(f'Executable mode changed: {relative}')
        if SECRET.search(data):
            errors.append(f'Secret material: {relative}')
        if source.suffix.lower() in {'.jar', '.class', '.pdf', '.cbl', '.cob', '.cpy', '.pem', '.key', '.db'}:
            errors.append(f'Excluded source type: {relative}')
        if relative.parts[0] == 'book' and str(relative) != 'book/COPYRIGHT.md':
            errors.append(f'Unbundled book source: {relative}')
        if source.suffix == '.md':
            content = data.decode('utf-8')
            if relative.parts[0] == 'demos' and CHINESE.search(content):
                errors.append(f'Non-English demo document: {relative}')
            for href in re.findall(r'\]\(([^)]+)\)', content):
                parsed = urlsplit(href)
                if parsed.scheme or parsed.netloc or not parsed.path:
                    continue
                if not (source.parent / unquote(parsed.path)).resolve().exists():
                    errors.append(f'Missing Markdown link: {relative}: {href}')
    for source in root.rglob('*'):
        relative = source.relative_to(root)
        if IGNORED.intersection(relative.parts):
            continue
        if source.is_symlink() or '.git' in relative.parts:
            errors.append(f'Excluded history or symlink: {relative}')
        elif source.is_file() and relative.as_posix() not in expected:
            errors.append(f'Unreviewed additional source: {relative}')
    if 'does not apply to `book/`' not in (root / 'LICENSE').read_text():
        errors.append('Missing code/book license boundary')
    if 'All rights reserved' not in (root / 'book/COPYRIGHT.md').read_text():
        errors.append('Missing book copyright boundary')
    return errors


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path, default=Path(__file__).resolve().parents[1] / 'examples')
    arguments = parser.parse_args()
    failures = validate(arguments.root)
    if failures:
        raise SystemExit('\n'.join(failures))
    print('Reviewed example hashes, modes, English documents, links and license boundaries passed.')
