import tempfile
import unittest
from pathlib import Path

from check_english_demo_docs import violations


class EnglishDemoDocumentationTest(unittest.TestCase):
    def test_english_documents_keep_identifiers_and_numbers(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            document = root / 'demos/petstore/docs/audit.md'
            document.parent.mkdir(parents=True)
            document.write_text('# Audit\n`WalletFundingAgreement`: 945 historical tests.\n')
            self.assertEqual([], violations(root))

    def test_nested_chinese_document_is_reported_without_copying_its_text(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            document = root / 'demos/bank-deposit/docs/legacy-source/audit.md'
            document.parent.mkdir(parents=True)
            document.write_text('# Audit\n\u4e2d\u6587\n', encoding='utf-8')
            self.assertEqual(['demos/bank-deposit/docs/legacy-source/audit.md:2: English translation required'], violations(root))

    def test_book_is_not_relicensed_or_selected_by_demo_gate(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            document = root / 'book/chapter.md'
            document.parent.mkdir()
            document.write_text('\u4e2d\u6587', encoding='utf-8')
            self.assertEqual([], violations(root))

    def test_readme_and_contribution_rules_are_checked(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            for name in ('README.md', 'AGENTS.md', 'README.zh-CN.md'):
                document = root / 'demos/petstore' / name
                document.parent.mkdir(parents=True, exist_ok=True)
                document.write_text('\u4e2d\u6587', encoding='utf-8')
            self.assertEqual(3, len(violations(root)))

    def test_generated_outputs_are_not_public_sources(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            document = root / 'demos/petstore/target/generated/README.md'
            document.parent.mkdir(parents=True)
            document.write_text('\u4e2d\u6587', encoding='utf-8')
            self.assertEqual([], violations(root))
