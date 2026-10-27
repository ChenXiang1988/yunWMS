import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import audit_delivery as audit  # noqa: E402


class PathRulesTest(unittest.TestCase):
    def test_empty_patterns_allow_everything(self):
        self.assertTrue(audit.path_matches("code/backend/Foo.java", []))

    def test_directory_glob(self):
        self.assertTrue(audit.path_matches("openspec/changes/x/proposal.md",
                                           ["openspec/changes/x/**"]))
        self.assertFalse(audit.path_matches("openspec/changes/y/proposal.md",
                                            ["openspec/changes/x/**"]))

    def test_single_star_glob(self):
        self.assertTrue(audit.path_matches("README.md", ["*.md"]))
        self.assertFalse(audit.path_matches("docs/api/README", ["*.md"]))

    def test_forbidden_extension(self):
        extensions = [".java", ".sql"]
        self.assertTrue(audit.has_forbidden_extension("A.java", extensions))
        self.assertFalse(audit.has_forbidden_extension("A.md", extensions))

    def test_empty_generated_patterns_match_nothing(self):
        self.assertFalse(audit.is_generated("README.md", []))
        self.assertTrue(audit.is_generated("docs/api/openapi.json", ["docs/api/openapi.json"]))


class ParsingTest(unittest.TestCase):
    def test_name_status_parser(self):
        output = "M\0README.md\0A\0docs/new.md\0"
        parsed = audit.parse_name_status(output)
        self.assertEqual(parsed, [
            {"status": "M", "path": "README.md"},
            {"status": "A", "path": "docs/new.md"},
        ])

    def test_numstat_parser(self):
        output = "3\t2\tREADME.md\n-\t-\timage.png\n"
        self.assertEqual(audit.parse_numstat(output), (3, 2, 5, ["README.md", "image.png"]))

    def test_generated_lines_are_excluded_from_commit_size(self):
        rows = [(500, 0, "docs/api/openapi.json"), (100, 0, "src/Main.java")]
        total, counted = audit.commit_totals(rows, ["docs/api/openapi.json"])
        self.assertEqual(total, 600)
        self.assertEqual(counted, 100)

    def test_openapi_count(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            path = root / "docs/api/openapi.json"
            path.parent.mkdir(parents=True)
            path.write_text(json.dumps({"paths": {
                "/a": {"get": {}, "post": {}},
                "/b": {"delete": {}},
            }}), encoding="utf-8")
            self.assertEqual(audit.count_openapi_operations(root), 3)

    def test_mapper_statement_count(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            mapper = root / "code/backend/wms-auth/src/main/resources/mapper/auth/X.xml"
            mapper.parent.mkdir(parents=True)
            mapper.write_text(
                '<mapper><select id="a"></select><update id="b"></update></mapper>',
                encoding="utf-8")
            self.assertEqual(audit.count_mapper_xml(root), 1)
            self.assertEqual(audit.count_mapper_statements(root), 2)


class CliTest(unittest.TestCase):
    def test_unknown_openspec_is_reported(self):
        with tempfile.TemporaryDirectory() as tmp:
            failed = subprocess.CompletedProcess(
                args=["openspec"], returncode=1, stdout="", stderr="not found")
            with mock.patch.object(audit, "run", return_value=failed):
                report = audit.openspec_evidence(Path(tmp), None)
            self.assertFalse(report["available"])
            self.assertIn("openspec list 不可用或失败", report["unknown"])


if __name__ == "__main__":
    unittest.main()
