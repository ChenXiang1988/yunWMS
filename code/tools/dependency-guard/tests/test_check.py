"""check_dependencies.py 的自检用例。

为什么需要：护栏自己错了比没有护栏更糟——它静默放行时的代价，
和它误报时被人加豁免绕过的代价，都高于它本身的价值。这批用例钉住的是
「哪些算依赖、哪些不算」，也就是护栏最容易判断错的地方。
"""

from __future__ import annotations

import json
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import check_dependencies as guard  # noqa: E402

POM_HEADER = '<project xmlns="http://maven.apache.org/POM/4.0.0">'


class CollectBackendTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)
        (self.root / "code" / "backend").mkdir(parents=True)

    def tearDown(self):
        self.tmp.cleanup()

    def write_pom(self, body: str, name: str = "pom.xml") -> None:
        (self.root / "code" / "backend" / name).write_text(
            f"{POM_HEADER}{body}</project>", encoding="utf-8")

    def test_收集声明的依赖(self):
        self.write_pom(
            "<dependencies><dependency>"
            "<groupId>org.example</groupId><artifactId>lib</artifactId>"
            "</dependency></dependencies>")
        self.assertEqual({"org.example:lib": "code/backend/pom.xml"},
                         guard.collect_backend_dependencies(self.root))

    def test_跳过本工程内部模块(self):
        self.write_pom(
            "<dependencies><dependency>"
            "<groupId>com.yunwms</groupId><artifactId>wms-web</artifactId>"
            "</dependency></dependencies>")
        self.assertEqual({}, guard.collect_backend_dependencies(self.root))

    def test_dependencyManagement_不算引入(self):
        # 版本管理不是依赖：把它算进来会误报一批「声明了但没用」的坐标
        self.write_pom(
            "<dependencyManagement><dependencies><dependency>"
            "<groupId>org.example</groupId><artifactId>managed</artifactId>"
            "</dependency></dependencies></dependencyManagement>")
        self.assertEqual({}, guard.collect_backend_dependencies(self.root))

    def test_子模块_pom_一并扫描(self):
        (self.root / "code" / "backend" / "wms-x").mkdir()
        (self.root / "code" / "backend" / "wms-x" / "pom.xml").write_text(
            f"{POM_HEADER}<dependencies><dependency>"
            "<groupId>org.example</groupId><artifactId>sub</artifactId>"
            "</dependency></dependencies></project>", encoding="utf-8")
        self.assertIn("org.example:sub", guard.collect_backend_dependencies(self.root))


class CollectFrontendTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)
        (self.root / "code" / "frontend" / "apps" / "demo").mkdir(parents=True)
        (self.root / "code" / "frontend" / "packages" / "ui-web").mkdir(parents=True)

    def tearDown(self):
        self.tmp.cleanup()

    def test_收集三类依赖段并跳过_workspace_包(self):
        (self.root / "code" / "frontend" / "apps" / "demo" / "package.json").write_text(
            json.dumps({
                "dependencies": {"vue": "^3.5.0", "@yunwms/tokens": "workspace:*"},
                "devDependencies": {"vite": "^6.0.0"},
                "peerDependencies": {"typescript": "~5.7.0"},
            }), encoding="utf-8")
        found = guard.collect_frontend_dependencies(self.root)
        self.assertEqual({"vue", "vite", "typescript"}, set(found))
        self.assertNotIn("@yunwms/tokens", found)


class EvaluateTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)
        (self.root / "code" / "backend").mkdir(parents=True)
        (self.root / "code" / "frontend").mkdir(parents=True)

    def tearDown(self):
        self.tmp.cleanup()

    def test_未登记的依赖报错(self):
        (self.root / "code" / "backend" / "pom.xml").write_text(
            f"{POM_HEADER}<dependencies><dependency>"
            "<groupId>com.nope</groupId><artifactId>unknown-lib</artifactId>"
            "</dependency></dependencies></project>", encoding="utf-8")
        errors, _ = guard.evaluate(self.root, {"backend": [], "frontend": []})
        self.assertEqual(1, len(errors))
        self.assertIn("com.nope:unknown-lib", errors[0])
        self.assertIn("allowlist.json", errors[0])

    def test_已登记的依赖通过(self):
        (self.root / "code" / "backend" / "pom.xml").write_text(
            f"{POM_HEADER}<dependencies><dependency>"
            "<groupId>com.ok</groupId><artifactId>known</artifactId>"
            "</dependency></dependencies></project>", encoding="utf-8")
        errors, warnings = guard.evaluate(
            self.root,
            {"backend": [{"coordinate": "com.ok:known", "version": "1",
                          "license": "MIT", "note": "x"}], "frontend": []})
        self.assertEqual([], errors)
        self.assertEqual([], warnings)

    def test_清单中未被使用的条目只警告不失败(self):
        errors, warnings = guard.evaluate(
            self.root,
            {"backend": [{"coordinate": "com.ok:stale", "version": "1",
                          "license": "MIT", "note": "x"}], "frontend": []})
        self.assertEqual([], errors)
        self.assertEqual(1, len(warnings))
        self.assertIn("com.ok:stale", warnings[0])

    def test_许可证未核实的条目告警(self):
        (self.root / "code" / "backend" / "pom.xml").write_text(
            f"{POM_HEADER}<dependencies><dependency>"
            "<groupId>com.ok</groupId><artifactId>known</artifactId>"
            "</dependency></dependencies></project>", encoding="utf-8")
        errors, warnings = guard.evaluate(
            self.root,
            {"backend": [{"coordinate": "com.ok:known", "version": "1",
                          "license": guard.UNVERIFIED, "note": "x"}], "frontend": []})
        self.assertEqual([], errors)
        self.assertIn(guard.UNVERIFIED, warnings[0])


if __name__ == "__main__":
    unittest.main()
