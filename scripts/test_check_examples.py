import hashlib
import json
from pathlib import Path
import tempfile
import unittest

from check_examples import validate


class ExampleImportTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        files = {'LICENSE': 'does not apply to `book/`',
                 'book/COPYRIGHT.md': 'All rights reserved',
                 'demos/sample/README.md': '# Example\n'}
        records = []
        for name, content in files.items():
            target = self.root / name
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text(content)
            records.append({'path': name, 'importedSha256': hashlib.sha256(target.read_bytes()).hexdigest(),
                            'executable': False})
        (self.root / 'IMPORT.json').write_text(json.dumps({'files': records}))

    def test_reviewed_import_passes(self):
        self.assertEqual([], validate(self.root))

    def test_changed_document_and_chinese_are_rejected(self):
        (self.root / 'demos/sample/README.md').write_text('\u4e2d\u6587')
        errors = validate(self.root)
        self.assertTrue(any('Unreviewed import change' in error for error in errors))
        self.assertTrue(any('Non-English' in error for error in errors))

    def test_extra_source_is_rejected_but_build_outputs_are_ignored(self):
        (self.root / 'target').mkdir()
        (self.root / 'target/generated.class').write_bytes(b'generated')
        self.assertEqual([], validate(self.root))
        (self.root / 'unexpected.java').write_text('unexpected')
        self.assertTrue(any('additional source' in error for error in validate(self.root)))

    def test_missing_link_is_rejected(self):
        (self.root / 'demos/sample/README.md').write_text('[missing](missing.md)')
        self.assertTrue(any('Missing Markdown link' in error for error in validate(self.root)))

    def test_history_is_rejected(self):
        (self.root / '.git').write_text('gitdir: elsewhere')
        self.assertTrue(any('history' in error for error in validate(self.root)))
