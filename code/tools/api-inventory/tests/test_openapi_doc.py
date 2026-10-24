# -*- coding: utf-8 -*-
"""openapi_doc 的判定测试。

只测「读出来的是什么」，不测排版。排版在 test_gen_api_inventory 里。
"""

from __future__ import annotations

import json
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT))

import openapi_doc as doc  # noqa: E402

FIXTURE = ROOT / "fixtures" / "openapi.sample.json"


def load_spec() -> dict:
    return json.loads(FIXTURE.read_text(encoding="utf-8"))


def schemas_of(spec: dict) -> dict:
    return spec["components"]["schemas"]


class OperationCollectionTest(unittest.TestCase):
    def test_counts_operations_not_java_methods(self) -> None:
        self.assertEqual(len(doc.collect_operations(load_spec())), 5)

    def test_filters_framework_paths(self) -> None:
        paths = {op.path for op in doc.collect_operations(load_spec())}
        self.assertNotIn("/error", paths)
        self.assertNotIn("/actuator/health", paths)

    def test_same_path_keeps_method_order(self) -> None:
        operations = [op for op in doc.collect_operations(load_spec()) if op.path == "/api/owners"]
        self.assertEqual([op.method for op in operations], ["GET", "POST"])

    def test_paths_sorted(self) -> None:
        paths = [op.path for op in doc.collect_operations(load_spec())]
        self.assertEqual(paths, sorted(paths))

    def test_custom_prefix(self) -> None:
        operations = doc.collect_operations(load_spec(), "/api/console/")
        self.assertEqual([op.path for op in operations], ["/api/console/categories"])


class ScopeTest(unittest.TestCase):
    def test_scope_by_prefix(self) -> None:
        self.assertEqual(doc.scope_of("/api/console/categories"), "console（仓网端）")
        self.assertEqual(doc.scope_of("/api/portal/profile"), "portal（货主端）")
        self.assertEqual(doc.scope_of("/api/auth/login"), "共用")


class ApiResultUnwrapTest(unittest.TestCase):
    def setUp(self) -> None:
        self.spec = load_spec()
        self.schemas = schemas_of(self.spec)
        self.operations = {(op.method, op.path): op.raw for op in doc.collect_operations(self.spec)}

    def test_wrapped_response_returns_data_schema(self) -> None:
        payload, wrapped = doc.response_payload(self.operations[("POST", "/api/auth/login")], self.schemas)
        self.assertTrue(wrapped)
        self.assertEqual(payload, {"$ref": "#/components/schemas/LoginResponse"})

    def test_unwrapped_response_returns_whole_schema(self) -> None:
        payload, wrapped = doc.response_payload(self.operations[("GET", "/api/portal/profile")], self.schemas)
        self.assertFalse(wrapped)
        self.assertEqual(payload, {"$ref": "#/components/schemas/OwnerVO"})

    def test_partial_shell_is_not_treated_as_apiresult(self) -> None:
        raw = {"responses": {"200": {"content": {"*/*": {"schema": {"type": "object", "properties": {"code": {"type": "integer"}, "data": {}}}}}}}}
        _, wrapped = doc.response_payload(raw, {})
        self.assertFalse(wrapped)


class PaginationTest(unittest.TestCase):
    def setUp(self) -> None:
        self.schemas = schemas_of(load_spec())

    def test_detects_page_result(self) -> None:
        schema = self.schemas["ApiResultPageResultOwnerVO"]["properties"]["data"]
        self.assertTrue(doc.is_paged(schema, self.schemas))

    def test_rejects_plain_object(self) -> None:
        schema = self.schemas["ApiResultOwnerVO"]["properties"]["data"]
        self.assertFalse(doc.is_paged(schema, self.schemas))

    def test_rejects_none(self) -> None:
        self.assertFalse(doc.is_paged(None, self.schemas))


class DerefTest(unittest.TestCase):
    def test_follows_chained_refs(self) -> None:
        schemas = {"A": {"$ref": "#/components/schemas/B"}, "B": {"type": "object", "properties": {"x": {}}}}
        self.assertEqual(doc.deref({"$ref": "#/components/schemas/A"}, schemas), schemas["B"])

    def test_missing_ref_becomes_empty(self) -> None:
        self.assertEqual(doc.deref({"$ref": "#/components/schemas/Nope"}, {}), {})


class LoadSourceTest(unittest.TestCase):
    def setUp(self) -> None:
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.dir = Path(self.tmp.name)

    def test_reads_file_and_reports_origin(self) -> None:
        spec, origin = doc.load_source(None, str(FIXTURE))
        self.assertIn("paths", spec)
        self.assertIn("文件快照", origin)

    def test_missing_file_raises(self) -> None:
        with self.assertRaises(doc.SourceError):
            doc.load_source(None, str(self.dir / "nope.json"))

    def test_invalid_json_raises(self) -> None:
        broken = self.dir / "broken.json"
        broken.write_text("{not json", encoding="utf-8")
        with self.assertRaises(doc.SourceError):
            doc.load_source(None, str(broken))

    def test_missing_paths_raises(self) -> None:
        empty = self.dir / "empty.json"
        empty.write_text('{"openapi": "3.0.1"}', encoding="utf-8")
        with self.assertRaises(doc.SourceError):
            doc.load_source(None, str(empty))


if __name__ == "__main__":
    unittest.main()
