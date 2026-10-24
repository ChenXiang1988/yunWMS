# -*- coding: utf-8 -*-
"""OpenAPI 文档的读取与结构判定。

与渲染分离，是为了让这部分能在不碰文件系统、不碰网络的前提下被单测：
台账脚本的价值在于「不依赖运行时也能核对」，如果判定逻辑必须先起服务才能测，
这条价值就没了。

这里只回答问题，不产生文本：有哪些 operation、响应有没有包 ApiResult、
业务数据是不是分页结构。怎么排版是 ``gen_api_inventory`` 的事。
"""

from __future__ import annotations

import json
import subprocess
import urllib.error
import urllib.request
from dataclasses import dataclass
from pathlib import Path

DEFAULT_URL = "http://localhost:8080/v3/api-docs"
DEFAULT_OUT = "docs/api/inventory.md"
DEFAULT_JSON_OUT = "docs/api/openapi.json"
DEFAULT_PREFIX = "/api/"

METHOD_ORDER = ("get", "post", "put", "delete", "patch", "head", "options", "trace")

# 判定 ApiResult 的三个必备字段。少一个就不当外壳处理，
# 因为「猜着解包」比「不解包」更危险：会把业务字段当成外壳吞掉。
SHELL_FIELDS = ("code", "message", "data")

# 判定分页的必备字段，取自 docs/api-convention.md 第四节。
PAGE_FIELDS = ("pageNo", "pageSize", "total", "records")


class SourceError(RuntimeError):
    """输入源不可用。对应进程退出码 2。"""


@dataclass(frozen=True)
class Operation:
    path: str
    method: str
    raw: dict


def ref_name(ref: str) -> str:
    return ref.rsplit("/", 1)[-1]


def deref(schema: dict, schemas: dict) -> dict:
    """把 ``$ref`` 解到底。

    环由调用方的访问栈负责，这里只防「A 引用 B、B 引用 A」这类定义本身成环
    的退化情形，用一个硬上限兜住，不参与业务判断。
    """
    seen = 0
    while isinstance(schema, dict) and "$ref" in schema and seen < 16:
        schema = schemas.get(ref_name(schema["$ref"]), {})
        seen += 1
    return schema if isinstance(schema, dict) else {}


def collect_operations(spec: dict, prefix: str = DEFAULT_PREFIX) -> list[Operation]:
    """按 path 字典序 + 方法固定序收集 operation。

    计数口径是 OpenAPI operation（path × method），不是 Java 方法数：
    一个 ``@PostMapping({"a","b"})`` 产出两个 operation，而
    ``GET /api/x`` 与 ``POST /api/x/search`` 是两条不同的接口。
    """
    paths = spec.get("paths") or {}
    operations: list[Operation] = []
    for path in sorted(paths):
        if prefix and not path.startswith(prefix):
            continue
        item = paths[path]
        if not isinstance(item, dict):
            continue
        for method in METHOD_ORDER:
            raw = item.get(method)
            if isinstance(raw, dict):
                operations.append(Operation(path=path, method=method.upper(), raw=raw))
    return operations


def scope_of(path: str) -> str:
    """按路径前缀判定鉴权主体，口径见 docs/api-convention.md 第一节。"""
    if path.startswith("/api/console/"):
        return "console（仓网端）"
    if path.startswith("/api/portal/"):
        return "portal（货主端）"
    return "共用"


def request_schema(raw: dict) -> dict | None:
    content = (raw.get("requestBody") or {}).get("content") or {}
    for media in sorted(content):
        schema = (content[media] or {}).get("schema")
        if schema:
            return schema
    return None


def response_payload(raw: dict, schemas: dict) -> tuple[dict | None, bool]:
    """取 2xx 响应，判断是否包了 ``ApiResult``。

    返回 ``(业务数据 schema, 是否已解包)``。未包外壳不算错误，但要如实标注——
    静默当成正常会让台账描述一个并不存在的外壳，前端照着写就会多解一层。
    """
    responses = raw.get("responses") or {}
    for code in sorted(responses):
        if not code.startswith("2"):
            continue
        content = (responses[code] or {}).get("content") or {}
        for media in sorted(content):
            schema = (content[media] or {}).get("schema")
            if not schema:
                continue
            properties = deref(schema, schemas).get("properties") or {}
            if set(SHELL_FIELDS) <= set(properties):
                return properties.get("data"), True
            return schema, False
    return None, False


def is_paged(schema: dict | None, schemas: dict) -> bool:
    if not schema:
        return False
    properties = deref(schema, schemas).get("properties") or {}
    return set(PAGE_FIELDS) <= set(properties)


def load_source(
    from_url: str | None, from_file: str | None, timeout: float = 10.0
) -> tuple[dict, str]:
    """读取 OpenAPI 文档，返回 ``(spec, 来源描述)``。

    来源描述会原样写进台账头部：实时拉取与文件快照必须能一眼分辨，
    否则旧快照会被当成当前契约用。
    """
    if from_file:
        path = Path(from_file)
        if not path.exists():
            raise SourceError(f"文件不存在：{path}")
        raw = path.read_text(encoding="utf-8")
        origin = f"文件快照 `{path}`"
    else:
        url = from_url or DEFAULT_URL
        try:
            with urllib.request.urlopen(url, timeout=timeout) as response:
                raw = response.read().decode("utf-8")
        except (urllib.error.URLError, OSError) as exc:
            raise SourceError(
                f"拉取 {url} 失败：{exc}\n"
                "后端起不来时用 --from-file 读已保存的 openapi.json，台账会标注为文件快照。"
            ) from exc
        origin = f"实时拉取 `{url}`"

    try:
        spec = json.loads(raw)
    except json.JSONDecodeError as exc:
        raise SourceError(f"JSON 非法：{exc}") from exc

    if not isinstance(spec, dict) or "paths" not in spec:
        raise SourceError("不是 OpenAPI 文档：顶层缺少 paths")
    return spec, origin


def short_commit() -> str:
    """取当前 HEAD 短哈希，用于标注台账对应哪一版代码。

    取不到就写 unknown，不猜也不留空——留空会让读者以为「没有 commit 概念」。
    """
    try:
        result = subprocess.run(
            ["git", "rev-parse", "--short", "HEAD"],
            capture_output=True,
            text=True,
            timeout=5,
            check=False,
        )
    except (OSError, subprocess.SubprocessError):
        return "unknown"
    return result.stdout.strip() or "unknown"
