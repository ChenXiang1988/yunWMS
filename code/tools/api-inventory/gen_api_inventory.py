#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""从 OpenAPI 文档生成接口台账。

真源是代码里的 ``@RequestMapping`` 与 DTO，本脚本只做派生物：

    运行时 /v3/api-docs → openapi.json（机器源）→ inventory.md（人读台账）

为什么不让台账手写：手抄的表格必然腐坏，而腐坏时没有任何机制能发现——
前端照着过期的表格写页面，直到联调才暴露。

为什么要求输出确定性：台账每次重跑都产生无意义 diff，「台账变了」这个信号
就失效了。因此路径按字典序、方法按固定序、字段按 schema 声明序，且不写
生成时间（时间每次都在变，commit 已能唯一定位版本）。

用法见同目录 README.md。
"""

from __future__ import annotations

import argparse
import json
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Iterable, Sequence

from openapi_doc import (
    DEFAULT_JSON_OUT,
    DEFAULT_OUT,
    DEFAULT_PREFIX,
    DEFAULT_URL,
    METHOD_ORDER,
    SourceError,
    collect_operations,
    is_paged,
    load_source,
    request_schema,
    response_payload,
    scope_of,
    short_commit,
)

MAX_DEPTH = 4
CYCLE_MARK = "↻"
TRUNC_MARK = "…"


@dataclass(frozen=True)
class Row:
    """字段表中的一行。path 用 ``a.b`` 表达嵌套，用 ``a[]`` 表达数组元素。"""

    path: str
    type_label: str
    required: str
    description: str


class Expander:
    """把 schema 展开成字段表行。

    三条规则：

    1. ``$ref`` 命中访问栈即标 ``↻`` 并停止下钻。DTO 有自引用（类目树），
       不设栈会无限展开；标号而不是静默截断，是为了让读者知道这里还有层级。
    2. 深度超过 ``MAX_DEPTH`` 标 ``…``，防止深层嵌套把台账撑爆。
    3. 对象字段本身也占一行，这样 ``owner | OwnerVO`` 与 ``owner.ownerCode``
       能同时出现在表里，读者才知道字段来自哪个类型。
    """

    def __init__(self, schemas: dict) -> None:
        self.schemas = schemas or {}

    def rows_for(self, schema: dict | None) -> list[Row]:
        if not schema:
            return []
        stack: tuple[str, ...] = ()
        if "$ref" in schema:
            name = schema["$ref"].rsplit("/", 1)[-1]
            stack = (name,)
            schema = self.schemas.get(name) or {}
        if schema.get("type") == "array" or "items" in schema:
            return self._emit_property("[]", schema, "", 0, stack)
        return self._emit_object(schema, "", 0, stack)

    def _emit_object(
        self, schema: dict, prefix: str, depth: int, stack: tuple[str, ...]
    ) -> list[Row]:
        if depth > MAX_DEPTH:
            return [Row(prefix or "…", TRUNC_MARK, "", "嵌套过深，已截断")]
        required = set(schema.get("required") or ())
        properties = schema.get("properties") or {}
        if not properties:
            return [Row(prefix or "—", "object", "", "无字段声明")]
        rows: list[Row] = []
        for name, child in properties.items():
            path = f"{prefix}.{name}" if prefix else name
            flag = "是" if name in required else "否"
            rows.extend(self._emit_property(path, child, flag, depth, stack))
        return rows

    def _emit_property(
        self, path: str, schema: dict, required: str, depth: int, stack: tuple[str, ...]
    ) -> list[Row]:
        description = (schema.get("description") or "").strip()

        if "$ref" in schema:
            name = schema["$ref"].rsplit("/", 1)[-1]
            if name in stack:
                return [
                    Row(path, f"{name} {CYCLE_MARK}", required, join_note(description, "自引用，已截断"))
                ]
            target = self.schemas.get(name)
            if not target:
                return [Row(path, f"{name}（未定义）", required, description)]
            head = Row(path, name, required, description)
            return [head] + self._emit_object(target, path, depth + 1, stack + (name,))

        if schema.get("type") == "array" or "items" in schema:
            return self._emit_array(path, schema, required, depth, stack, description)

        if schema.get("type") == "object" or "properties" in schema:
            head = Row(path, "object", required, description)
            return [head] + self._emit_object(schema, path, depth + 1, stack)

        return [Row(path, scalar_label(schema), required, description)]

    def _emit_array(
        self,
        path: str,
        schema: dict,
        required: str,
        depth: int,
        stack: tuple[str, ...],
        description: str,
    ) -> list[Row]:
        items = schema.get("items") or {}

        if "$ref" in items:
            name = items["$ref"].rsplit("/", 1)[-1]
            if name in stack:
                return [
                    Row(path, f"{name}[] {CYCLE_MARK}", required, join_note(description, "自引用，已截断"))
                ]
            target = self.schemas.get(name)
            if not target:
                return [Row(path, f"{name}[]（未定义）", required, description)]
            head = Row(path, f"{name}[]", required, description)
            return [head] + self._emit_object(target, f"{path}[]", depth + 1, stack + (name,))

        if items.get("type") == "object" or "properties" in items:
            head = Row(path, "object[]", required, description)
            return [head] + self._emit_object(items, f"{path}[]", depth + 1, stack)

        return [Row(path, f"{scalar_label(items)}[]", required, description)]


def scalar_label(schema: dict) -> str:
    if "allOf" in schema:
        return "object"
    kind = schema.get("type")
    if not kind:
        return "object" if "properties" in schema else "any"
    label = kind
    fmt = schema.get("format")
    if fmt:
        label = f"{kind}({fmt})"
    if isinstance(schema.get("enum"), list):
        label += " enum(" + ", ".join(str(value) for value in schema["enum"]) + ")"
    if schema.get("nullable"):
        label += "?"
    return label


def join_note(description: str, note: str) -> str:
    return f"{description}；{note}" if description else note


def md_cell(value: Any) -> str:
    text = str(value).replace("\r", " ").replace("\n", " ").replace("|", "\\|").strip()
    return text or "—"


def md_table(headers: Sequence[str], rows: Iterable[Sequence[Any]]) -> list[str]:
    lines = [
        "| " + " | ".join(headers) + " |",
        "| " + " | ".join("---" for _ in headers) + " |",
    ]
    for row in rows:
        lines.append("| " + " | ".join(md_cell(cell) for cell in row) + " |")
    return lines


def render(spec: dict, origin: str, commit: str, prefix: str = DEFAULT_PREFIX) -> str:
    schemas = ((spec.get("components") or {}).get("schemas")) or {}
    operations = collect_operations(spec, prefix)
    expander = Expander(schemas)

    lines: list[str] = [
        "# 接口台账",
        "",
        "> **生成物，禁止手改。** 由 `code/tools/api-inventory/gen_api_inventory.py` 生成。",
        f"> 来源：{origin}",
        f"> 源 commit：`{commit}`",
        "> 计数口径：OpenAPI operation（path × method），不是 Java 方法数",
        "> 字段记号：`a.b` 嵌套，`a[]` 数组元素，`↻` 自引用已截断，`…` 嵌套过深已截断",
        "",
        "## 汇总",
        "",
    ]

    by_method: dict[str, int] = {}
    by_scope: dict[str, int] = {}
    for operation in operations:
        by_method[operation.method] = by_method.get(operation.method, 0) + 1
        scope = scope_of(operation.path)
        by_scope[scope] = by_scope.get(scope, 0) + 1

    summary_rows: list[tuple[str, Any]] = [("接口总数", len(operations))]
    summary_rows += [
        (method.upper(), by_method[method.upper()])
        for method in METHOD_ORDER
        if method.upper() in by_method
    ]
    lines.extend(md_table(("项", "值"), summary_rows))
    lines.append("")
    lines.extend(
        md_table(
            ("主体", "数量"),
            [
                (scope, by_scope[scope])
                for scope in ("console（仓网端）", "portal（货主端）", "共用")
                if scope in by_scope
            ],
        )
    )
    lines.extend(
        [
            "",
            "## 统一响应外壳",
            "",
            "所有接口返回 `ApiResult`，HTTP 状态码固定 200；仅未认证 401、无权限 403 例外。",
            "",
        ]
    )
    lines.extend(
        md_table(
            ("字段", "类型", "说明"),
            [
                ("`code`", "integer", "0 表示成功，非 0 见错误码表"),
                ("`message`", "string", "提示文本"),
                ("`data`", "T", "业务数据，下方逐接口展开"),
            ],
        )
    )
    lines.extend(["", "下文「返回字段」均指 `data` 内部，不含外壳。", "", "## 接口清单", ""])

    if not operations:
        lines.extend([f"当前没有以 `{prefix}` 开头的接口。", ""])
        return "\n".join(lines)

    lines.extend([f"共 {len(operations)} 个接口。", ""])

    for index, operation in enumerate(operations, start=1):
        lines.extend([f"### {index}. `{operation.method} {operation.path}`", ""])
        summary = (operation.raw.get("summary") or "").strip()
        description = (operation.raw.get("description") or "").strip()
        if summary:
            lines.extend([summary, ""])
        if description and description != summary:
            lines.extend([description, ""])
        if operation.raw.get("deprecated"):
            lines.extend(["> 已废弃。", ""])

        payload, wrapped = response_payload(operation.raw, schemas)
        lines.extend(
            [
                f"- 主体：{scope_of(operation.path)}",
                f"- 分页：{'是' if is_paged(payload, schemas) else '否'}",
                f"- 响应外壳：{'`ApiResult`' if wrapped else '未包 `ApiResult`（不符合 `docs/api-convention.md`）'}",
                "",
            ]
        )

        request = request_schema(operation.raw)
        if request:
            rows = [(f"`{r.path}`", r.type_label, r.required, r.description) for r in expander.rows_for(request)]
            lines.extend(["**请求字段**", ""] + md_table(("字段", "类型", "必填", "说明"), rows) + [""])
        else:
            lines.extend(["**请求字段**", "", "无请求体。", ""])

        payload_rows = expander.rows_for(payload)
        if payload_rows:
            rows = [(f"`{r.path}`", r.type_label, r.description) for r in payload_rows]
            lines.extend(["**返回字段**", ""] + md_table(("字段", "类型", "说明"), rows) + [""])
        else:
            lines.extend(["**返回字段**", "", "无业务数据。", ""])

    return "\n".join(lines)


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="gen_api_inventory.py",
        description="从 OpenAPI 文档生成接口台账，输出为确定性文本。",
    )
    source = parser.add_mutually_exclusive_group()
    source.add_argument("--from-url", default=DEFAULT_URL, help=f"从运行中的服务拉取（默认 {DEFAULT_URL}）")
    source.add_argument("--from-file", help="从已保存的 openapi.json 读取（降级路径）")
    parser.add_argument("--out", default=DEFAULT_OUT, help=f"台账输出路径（默认 {DEFAULT_OUT}）")
    parser.add_argument(
        "--json-out", default=DEFAULT_JSON_OUT, help=f"openapi.json 输出路径（默认 {DEFAULT_JSON_OUT}）"
    )
    parser.add_argument("--prefix", default=DEFAULT_PREFIX, help=f"只收录该前缀的路径（默认 {DEFAULT_PREFIX}）")
    parser.add_argument("--timeout", type=float, default=10.0, help="拉取超时秒数（默认 10）")
    parser.add_argument("--check", action="store_true", help="只校验台账与源是否一致，不写文件；不一致返回 1")
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    args = build_parser().parse_args(argv)

    try:
        spec, origin = load_source(args.from_url, args.from_file, args.timeout)
    except SourceError as exc:
        print(f"错误：{exc}", file=sys.stderr)
        return 2

    text = render(spec, origin, short_commit(), args.prefix)
    out_path = Path(args.out)

    if args.check:
        if not out_path.exists():
            print(f"不一致：{out_path} 不存在。先不带 --check 生成一次。", file=sys.stderr)
            return 1
        if out_path.read_text(encoding="utf-8") != text:
            print(f"不一致：{out_path} 与源生成的台账不同。", file=sys.stderr)
            return 1
        print(f"一致：{out_path}")
        return 0

    out_path.parent.mkdir(parents=True, exist_ok=True)
    out_path.write_text(text, encoding="utf-8")
    print(f"已写入：{out_path}（{len(collect_operations(spec, args.prefix))} 个接口）")

    if not args.from_file and args.json_out:
        json_path = Path(args.json_out)
        json_path.parent.mkdir(parents=True, exist_ok=True)
        json_path.write_text(json.dumps(spec, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print(f"已写入：{json_path}")

    return 0


if __name__ == "__main__":
    sys.exit(main())
