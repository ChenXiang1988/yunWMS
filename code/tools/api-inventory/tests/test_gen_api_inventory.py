# -*- coding: utf-8 -*-
"""gen_api_inventory 的渲染与命令行测试。

测试只依赖 fixtures，不启动后端：台账脚本的价值在于「不依赖运行时也能核对」，
如果测试本身要求先起服务，这条价值就没了。
"""

from __future__ import annotations

import json
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT))

import gen_api_inventory as gen  # noqa: E402
from openapi_doc import DEFAULT_PREFIX  # noqa: E402

FIXTURE = ROOT / "fixtures" / "openapi.sample.json"


def load_spec() -> dict:
    return json.loads(FIXTURE.read_text(encoding="utf-8"))


def render_sample() -> str:
    return gen.render(load_spec(), "文件快照 `fixtures/openapi.sample.json`", "deadbee", DEFAULT_PREFIX)


def section_of(text: str, heading: str) -> str:
    """截取某个接口小节，避免测试依赖接口在清单里的序号。"""
    return text.split(heading, 1)[1].split("### ", 1)[0]


class RenderStructureTest(unittest.TestCase):
    def test_header_declares_generated_artifact(self) -> None:
        self.assertIn("生成物，禁止手改", render_sample())

    def test_counts_in_summary(self) -> None:
        text = render_sample()
        summary = text.split("## 汇总", 1)[1].split("## 统一响应外壳", 1)[0]
        self.assertIn("| 接口总数 | 5 |", summary)
        self.assertIn("| GET | 3 |", summary)
        self.assertIn("| POST | 2 |", summary)

    def test_scope_breakdown(self) -> None:
        text = render_sample()
        summary = text.split("## 汇总", 1)[1].split("## 统一响应外壳", 1)[0]
        self.assertIn("| console（仓网端） | 1 |", summary)
        self.assertIn("| portal（货主端） | 1 |", summary)
        self.assertIn("| 共用 | 3 |", summary)

    def test_shell_documented_once(self) -> None:
        text = render_sample()
        shell = text.split("## 统一响应外壳", 1)[1].split("## 接口清单", 1)[0]
        for field in ("`code`", "`message`", "`data`"):
            self.assertIn(field, shell)


class FieldTableTest(unittest.TestCase):
    def test_required_flag(self) -> None:
        section = section_of(render_sample(), "`POST /api/auth/login`")
        self.assertIn("| `loginName` | string | 是 |", section)

    def test_optional_flag(self) -> None:
        section = section_of(render_sample(), "`POST /api/owners`")
        self.assertIn("| `contactName` | string? | 否 |", section)

    def test_enum_label(self) -> None:
        self.assertIn("enum(CONSOLE, PORTAL)", render_sample())

    def test_int64_format_kept(self) -> None:
        self.assertIn("integer(int64)", render_sample())

    def test_nullable_marked(self) -> None:
        section = section_of(render_sample(), "`POST /api/owners`")
        self.assertIn("string?", section)

    def test_wrapped_response_excludes_shell_fields(self) -> None:
        section = section_of(render_sample(), "`POST /api/auth/login`")
        self.assertIn("`token`", section)
        self.assertNotIn("| `code` |", section)
        self.assertNotIn("| `message` |", section)

    def test_wrapped_response_marked(self) -> None:
        section = section_of(render_sample(), "`POST /api/auth/login`")
        self.assertIn("响应外壳：`ApiResult`", section)

    def test_unwrapped_response_marked(self) -> None:
        section = section_of(render_sample(), "`GET /api/portal/profile`")
        self.assertIn("未包 `ApiResult`", section)

    def test_no_request_body_section(self) -> None:
        section = section_of(render_sample(), "`GET /api/portal/profile`")
        self.assertIn("无请求体。", section)


class PaginationRenderTest(unittest.TestCase):
    def test_paged_flag(self) -> None:
        section = section_of(render_sample(), "`GET /api/owners`")
        self.assertIn("分页：是", section)

    def test_expands_record_element_fields(self) -> None:
        section = section_of(render_sample(), "`GET /api/owners`")
        self.assertIn("`records[].ownerCode`", section)
        self.assertIn("`records[].status`", section)


class CycleRenderTest(unittest.TestCase):
    def test_self_reference_marked_and_stopped(self) -> None:
        section = section_of(render_sample(), "`GET /api/console/categories`")
        self.assertIn(gen.CYCLE_MARK, section)
        self.assertNotIn("children[].children", section)

    def test_cycle_note_is_readable(self) -> None:
        section = section_of(render_sample(), "`GET /api/console/categories`")
        self.assertIn("自引用，已截断", section)


class DeterminismTest(unittest.TestCase):
    def test_two_renders_are_byte_identical(self) -> None:
        self.assertEqual(render_sample(), render_sample())

    def test_no_wall_clock_in_output(self) -> None:
        self.assertNotIn("生成时间", render_sample())


class CliTest(unittest.TestCase):
    def setUp(self) -> None:
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.out = Path(self.tmp.name) / "inventory.md"

    def test_generate_then_check_returns_zero(self) -> None:
        self.assertEqual(gen.main(["--from-file", str(FIXTURE), "--out", str(self.out)]), 0)
        self.assertTrue(self.out.exists())
        self.assertEqual(gen.main(["--from-file", str(FIXTURE), "--out", str(self.out), "--check"]), 0)

    def test_check_detects_drift(self) -> None:
        gen.main(["--from-file", str(FIXTURE), "--out", str(self.out)])
        self.out.write_text(self.out.read_text(encoding="utf-8") + "\n篡改\n", encoding="utf-8")
        self.assertEqual(gen.main(["--from-file", str(FIXTURE), "--out", str(self.out), "--check"]), 1)

    def test_check_without_artifact_returns_one(self) -> None:
        self.assertEqual(gen.main(["--from-file", str(FIXTURE), "--out", str(self.out), "--check"]), 1)

    def test_missing_file_returns_two(self) -> None:
        self.assertEqual(gen.main(["--from-file", str(Path(self.tmp.name) / "nope.json"), "--out", str(self.out)]), 2)

    def test_custom_prefix(self) -> None:
        gen.main(["--from-file", str(FIXTURE), "--out", str(self.out), "--prefix", "/api/console/"])
        text = self.out.read_text(encoding="utf-8")
        self.assertIn("共 1 个接口", text)
        self.assertNotIn("/api/auth/login", text)


if __name__ == "__main__":
    unittest.main()
