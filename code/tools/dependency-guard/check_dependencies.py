#!/usr/bin/env python3
"""依赖准入核对：代码里出现的每个直接依赖，都必须在 allowlist.json 里登记过。

为什么需要它：AGENTS.md 要求引入依赖前确认「许可证、最近维护时间、可否被现有依赖替代」，
但这条要求此前只写在文档里——加一个新依赖不会让任何东西变红。本脚本把准入清单变成构建输入，
于是「加依赖」这件事必须先登记，登记就必须回答那三个问题。

为什么是「未登记即失败」而不是「许可证白名单」：许可证白名单需要外部许可证数据库，
且 Maven 侧有一批依赖（sa-token、springdoc、swagger 注解、Flyway）压根不在 pom 里声明
licenses 元数据，靠元数据判许可证会制造大量假阴性。反过来，要求每个依赖都显式登记并
填写许可证与理由，才能把「没人评审过的库」挡在外面。坐标黑名单那层由后端 enforcer 承担。

用法：
    python3 check_dependencies.py           # 人类可读输出
    python3 check_dependencies.py --json    # 机器可读，供后续接入

退出码：0 通过（可能带警告）；1 存在未登记依赖。
"""

from __future__ import annotations

import argparse
import json
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

MAVEN_NS = "{http://maven.apache.org/POM/4.0.0}"
INTERNAL_GROUP_ID = "com.yunwms"
WORKSPACE_SPEC_PREFIX = "workspace:"
UNVERIFIED = "UNVERIFIED"

ALLOWLIST_NAME = "allowlist.json"
DEPENDENCY_SECTIONS = ("dependencies", "devDependencies", "peerDependencies")


def repo_root() -> Path:
    """仓库根：本文件位于 <root>/code/tools/dependency-guard/。"""
    return Path(__file__).resolve().parents[3]


def collect_backend_dependencies(root: Path) -> dict[str, str]:
    """收集后端各模块 pom 的直接依赖，返回 {坐标: 首次出现的文件}。

    只看 <dependencies>，不看 <dependencyManagement>：后者是版本管理，不是引入。
    跳过本工程内部模块 com.yunwms:*，它们是模块不是依赖。
    """
    found: dict[str, str] = {}
    backend = root / "code" / "backend"
    poms = [backend / "pom.xml", *sorted(backend.glob("*/pom.xml"))]

    for pom in poms:
        if not pom.is_file():
            continue
        dependencies = ET.parse(pom).getroot().find(f"{MAVEN_NS}dependencies")
        if dependencies is None:
            continue
        for dependency in dependencies.findall(f"{MAVEN_NS}dependency"):
            group_id = (dependency.findtext(f"{MAVEN_NS}groupId") or "").strip()
            artifact_id = (dependency.findtext(f"{MAVEN_NS}artifactId") or "").strip()
            if not group_id or not artifact_id or group_id == INTERNAL_GROUP_ID:
                continue
            coordinate = f"{group_id}:{artifact_id}"
            found.setdefault(coordinate, pom.relative_to(root).as_posix())
    return found


def collect_frontend_dependencies(root: Path) -> dict[str, str]:
    """收集前端根、apps、packages 的 package.json 直接依赖，返回 {包名: 首次出现的文件}。

    跳过 workspace: 协议的内部包，它们与本工程内部模块同理。
    """
    found: dict[str, str] = {}
    frontend = root / "code" / "frontend"
    manifests = [
        frontend / "package.json",
        *sorted((frontend / "apps").glob("*/package.json")),
        *sorted((frontend / "packages").glob("*/package.json")),
    ]

    for manifest in manifests:
        if not manifest.is_file():
            continue
        package = json.loads(manifest.read_text(encoding="utf-8"))
        for section in DEPENDENCY_SECTIONS:
            for name, spec in (package.get(section) or {}).items():
                if str(spec).startswith(WORKSPACE_SPEC_PREFIX):
                    continue
                found.setdefault(name, manifest.relative_to(root).as_posix())
    return found


def load_allowlist(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def evaluate(root: Path, allowlist: dict) -> tuple[list[str], list[str]]:
    """比对代码与清单，返回 (错误, 警告)。错误非空即应让构建失败。"""
    errors: list[str] = []
    warnings: list[str] = []

    backend_allowed = {item["coordinate"]: item for item in allowlist.get("backend", [])}
    frontend_allowed = {item["name"]: item for item in allowlist.get("frontend", [])}

    for coordinate, where in sorted(collect_backend_dependencies(root).items()):
        if coordinate not in backend_allowed:
            errors.append(
                f"后端依赖 {coordinate} 未在准入清单登记（出现于 {where}）。\n"
                f"    登记方式：在 dependency-guard/{ALLOWLIST_NAME} 的 backend 段追加\n"
                f'    {{"coordinate": "{coordinate}", "version": "<实际版本>", '
                f'"license": "<许可证>", "note": "<为什么是它>"}}\n'
                f"    license 必须从依赖自身的元数据核实（pom 的 licenses 段、jar 内 "
                f"LICENSE.txt、npm registry 的 license 字段），不得凭印象填写；\n"
                f"    核实不到就填 {UNVERIFIED} 并在 note 写明原因，它会作为警告出现但不会阻断构建。"
            )

    for name, where in sorted(collect_frontend_dependencies(root).items()):
        if name not in frontend_allowed:
            errors.append(
                f"前端依赖 {name} 未在准入清单登记（出现于 {where}）。\n"
                f"    登记方式：在 dependency-guard/{ALLOWLIST_NAME} 的 frontend 段追加\n"
                f'    {{"name": "{name}", "version": "<声明范围>", '
                f'"license": "<许可证>", "note": "<为什么是它>"}}'
            )

    # 清单里有、代码里没用：只提示。父 pom 的 dependencyManagement 允许「声明供后续模块使用」，
    # 把它做成失败会让构建变脆——漏删一条清单记录的代价，远小于误报阻断提交的代价。
    backend_actual = set(collect_backend_dependencies(root))
    frontend_actual = set(collect_frontend_dependencies(root))
    for coordinate in sorted(set(backend_allowed) - backend_actual):
        warnings.append(f"清单中的后端依赖 {coordinate} 当前没有模块引用，确认后请删除该条")
    for name in sorted(set(frontend_allowed) - frontend_actual):
        warnings.append(f"清单中的前端依赖 {name} 当前没有包引用，确认后请删除该条")

    for item in [*allowlist.get("backend", []), *allowlist.get("frontend", [])]:
        label = item.get("coordinate") or item.get("name")
        if not item.get("license") or item["license"] == UNVERIFIED:
            warnings.append(f"{label} 的许可证未核实（{UNVERIFIED}），登记时必须补齐")

    return errors, warnings


def main() -> int:
    # Windows 控制台默认 GBK，直接打印 ✔ 会抛 UnicodeEncodeError，把一次「通过」变成一次崩溃。
    # 统一成 UTF-8 输出：符号与既有 check-ui-boundaries.mjs 保持一致，且 CI 的 UTF-8 环境不受影响。
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")

    parser = argparse.ArgumentParser(description="依赖准入核对")
    parser.add_argument("--json", action="store_true", help="以 JSON 输出，供后续接入")
    parser.add_argument("--root", type=Path, default=None, help="仓库根，默认按本文件位置推导")
    args = parser.parse_args()

    root = args.root or repo_root()
    allowlist = load_allowlist(Path(__file__).resolve().parent / ALLOWLIST_NAME)
    errors, warnings = evaluate(root, allowlist)

    if args.json:
        print(json.dumps({"errors": errors, "warnings": warnings}, ensure_ascii=False, indent=2))
        return 1 if errors else 0

    for warning in warnings:
        print(f"[警告] {warning}")
    for error in errors:
        print(f"[错误] {error}")

    if errors:
        print(f"\n✖ 依赖准入核对未通过：{len(errors)} 个依赖未登记，{len(warnings)} 条警告")
        return 1

    print(f"✔ 依赖准入核对通过（{len(warnings)} 条警告）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
