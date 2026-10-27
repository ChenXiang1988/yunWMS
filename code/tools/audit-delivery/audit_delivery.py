#!/usr/bin/env python3
"""只读交付审计证据生成器。

输出 JSON 到 stdout，不写任何文件。审计范围由 --anchor、--allow、
--forbid-ext 与 --generated 明确给出，脚本不自行推断白名单。
"""

from __future__ import annotations

import argparse
import fnmatch
import json
import re
import subprocess
import sys
from pathlib import Path
from typing import Any

DEFAULT_FORBIDDEN_EXTENSIONS = (".java", ".sql", ".vue", ".ts", ".tsx", ".css")


def run(command: list[str], cwd: Path) -> subprocess.CompletedProcess[str]:
    return subprocess.run(command, cwd=cwd, capture_output=True, text=True, check=False)


def repo_root() -> Path:
    result = run(["git", "rev-parse", "--show-toplevel"], Path.cwd())
    if result.returncode != 0:
        raise RuntimeError("当前目录不在 Git 仓库内")
    return Path(result.stdout.strip())


def path_matches(path: str, patterns: list[str]) -> bool:
    """匹配允许路径；`dir/**` 表示目录下全部文件。"""
    if not patterns:
        return True
    for pattern in patterns:
        if pattern.endswith("/**"):
            if path == pattern[:-3] or path.startswith(pattern[:-2]):
                return True
        elif fnmatch.fnmatchcase(path, pattern):
            return True
    return False


def has_forbidden_extension(path: str, extensions: list[str]) -> bool:
    return any(path.endswith(ext) for ext in extensions)


def is_generated(path: str, patterns: list[str]) -> bool:
    """生成物规则为空时表示没有生成物，而不是所有文件都是生成物。"""
    return bool(patterns) and path_matches(path, patterns)


def parse_name_status(output: str) -> list[dict[str, str]]:
    parts = output.split("\0")
    files: list[dict[str, str]] = []
    index = 0
    while index + 1 < len(parts):
        status = parts[index].strip()
        path = parts[index + 1]
        if status and path:
            files.append({"status": status, "path": path})
        index += 2
    return files


def parse_numstat_rows(output: str) -> list[tuple[int, int, str]]:
    rows: list[tuple[int, int, str]] = []
    for line in output.splitlines():
        columns = line.split("\t")
        if len(columns) < 3:
            continue
        add, remove, path = columns[0], columns[1], columns[2]
        rows.append((int(add) if add.isdigit() else 0,
                     int(remove) if remove.isdigit() else 0,
                     path))
    return rows


def parse_numstat(output: str) -> tuple[int, int, int, list[str]]:
    rows = parse_numstat_rows(output)
    added = sum(row[0] for row in rows)
    deleted = sum(row[1] for row in rows)
    return added, deleted, added + deleted, [row[2] for row in rows]


def commit_totals(rows: list[tuple[int, int, str]], generated: list[str]) -> tuple[int, int]:
    total = sum(added + deleted for added, deleted, _ in rows)
    counted = sum(added + deleted for added, deleted, path in rows
                  if not is_generated(path, generated))
    return total, counted


def count_mapper_statements(root: Path) -> int:
    pattern = re.compile(r"<(select|insert|update|delete)\s+[^>]*\bid=")
    total = 0
    for path in root.glob("code/backend/**/src/main/resources/mapper/**/*.xml"):
        total += len(pattern.findall(path.read_text(encoding="utf-8")))
    return total


def count_mapper_xml(root: Path) -> int:
    return sum(1 for _ in root.glob("code/backend/**/src/main/resources/mapper/**/*.xml"))


def count_migrations(root: Path) -> int:
    return sum(1 for _ in root.glob("code/backend/wms-app/src/main/resources/db/migration/V*.sql"))


def count_tables(root: Path) -> int:
    pattern = re.compile(r"CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?([a-zA-Z0-9_]+)", re.IGNORECASE)
    tables: set[str] = set()
    for path in root.glob("code/backend/wms-app/src/main/resources/db/migration/V*.sql"):
        tables.update(pattern.findall(path.read_text(encoding="utf-8")))
    return len(tables)


def count_openapi_operations(root: Path) -> int:
    path = root / "docs/api/openapi.json"
    if not path.exists():
        return 0
    data = json.loads(path.read_text(encoding="utf-8"))
    methods = {"get", "post", "put", "patch", "delete", "head", "options", "trace"}
    return sum(
        1
        for path_item in data.get("paths", {}).values()
        for method in path_item
        if method.lower() in methods
    )


def openspec_evidence(root: Path, change: str | None) -> dict[str, Any]:
    result: dict[str, Any] = {"available": True, "unknown": []}
    listed = run(["openspec", "list", "--json"], root)
    if listed.returncode != 0:
        result["available"] = False
        result["unknown"].append("openspec list 不可用或失败")
        return result
    try:
        result["changes"] = json.loads(listed.stdout).get("changes", [])
    except json.JSONDecodeError:
        result["available"] = False
        result["unknown"].append("openspec list 输出不是合法 JSON")
        return result
    if change:
        validated = run(["openspec", "validate", change], root)
        result["validate"] = {
            "change": change,
            "ok": validated.returncode == 0,
            "output": (validated.stdout or validated.stderr).strip(),
        }
        if validated.returncode != 0:
            result["unknown"].append("openspec validate 未通过或无法执行")
    return result


def collect(root: Path, anchor: str, staged: bool) -> dict[str, Any]:
    head = run(["git", "rev-parse", "HEAD"], root).stdout.strip()
    branch = run(["git", "rev-parse", "--abbrev-ref", "HEAD"], root).stdout.strip()
    merge_base = run(["git", "merge-base", anchor, "HEAD"], root)
    merge_base_sha = merge_base.stdout.strip() if merge_base.returncode == 0 else None

    diff_command = ["git", "diff", "--name-status", "-z", "--no-renames"]
    if staged:
        diff_command.append("--cached")
    else:
        diff_command.append(f"{anchor}..HEAD")
    changed = parse_name_status(run(diff_command, root).stdout)

    commits: list[dict[str, Any]] = []
    if not staged:
        shas = run(["git", "rev-list", "--reverse", f"{anchor}..HEAD"], root).stdout.split()
        for sha in shas:
            numstat = run(["git", "show", "--numstat", "--format=", "--no-renames", sha], root).stdout
            rows = parse_numstat_rows(numstat)
            added = sum(row[0] for row in rows)
            deleted = sum(row[1] for row in rows)
            subject = run(["git", "show", "-s", "--format=%s", sha], root).stdout.strip()
            commits.append({"sha": sha, "subject": subject, "added": added,
                            "deleted": deleted, "total": added + deleted,
                            "rows": rows})
    return {
        "head": head,
        "branch": branch,
        "anchor": anchor,
        "mergeBase": merge_base_sha,
        "changedFiles": changed,
        "commits": commits,
    }


def build_report(args: argparse.Namespace) -> tuple[dict[str, Any], list[str]]:
    root = repo_root()
    base = collect(root, args.anchor, args.staged)
    raw_extensions = args.forbid_ext or list(DEFAULT_FORBIDDEN_EXTENSIONS)
    forbidden_ext = [ext if ext.startswith(".") else "." + ext for ext in raw_extensions]
    blocking: list[str] = []

    changed_paths = [item["path"] for item in base["changedFiles"]]
    outside = [path for path in changed_paths if not path_matches(path, args.allow)]
    forbidden = [path for path in changed_paths if has_forbidden_extension(path, forbidden_ext)]
    generated = [path for path in changed_paths if is_generated(path, args.generated)]
    if outside:
        blocking.append("存在白名单外路径")
    if forbidden:
        blocking.append("存在禁止扩展名文件")
    if generated:
        blocking.append("存在生成物改动")

    for commit in base["commits"]:
        total, counted = commit_totals(commit.pop("rows"), args.generated)
        commit["countedTotal"] = counted
        if counted > 400:
            blocking.append(f"提交 {commit['sha'][:8]} 超过 400 行：{counted}（含生成物合计 {total}）")

    unknowns: list[str] = []
    counts: dict[str, Any] = {}
    for name, fn in {
        "mapperXml": count_mapper_xml,
        "mapperStatements": count_mapper_statements,
        "migrations": count_migrations,
        "tables": count_tables,
        "openapiOperations": count_openapi_operations,
    }.items():
        try:
            counts[name] = fn(root)
        except Exception as exc:  # 单个统计失败不阻断其他证据
            counts[name] = None
            unknowns.append(f"{name} 统计失败：{exc}")

    openspec = {"available": False, "unknown": ["未请求 openspec 证据"]}
    if not args.no_openspec:
        openspec = openspec_evidence(root, args.change)

    report = {
        "schemaVersion": 1,
        "repository": {
            "root": str(root),
            "branch": base["branch"],
            "head": base["head"],
            "anchor": base["anchor"],
            "mergeBase": base["mergeBase"],
            "staged": args.staged,
        },
        "scope": {
            "allow": args.allow,
            "forbidExtensions": forbidden_ext,
            "generated": args.generated,
        },
        "changedFiles": base["changedFiles"],
        "violations": {
            "outsideAllowlist": outside,
            "forbiddenFiles": forbidden,
            "generatedFiles": generated,
        },
        "commits": base["commits"],
        "counts": counts,
        "openspec": openspec,
        "unknowns": unknowns + openspec.get("unknown", []),
        "blocking": blocking,
    }
    return report, blocking


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="生成只读交付审计证据")
    parser.add_argument("--anchor", required=True, help="用于比较的 Git revision，例如 main")
    parser.add_argument("--allow", action="append", default=[],
                        help="允许路径模式，可重复；dir/** 表示目录下全部文件")
    parser.add_argument("--forbid-ext", action="append", default=None,
                        help="禁止的扩展名，可重复；不传时使用 Java/SQL/Vue/TS/CSS 默认集")
    parser.add_argument("--generated", action="append", default=[],
                        help="生成物路径模式，可重复；命中时报告改动并从提交行数扣除")
    parser.add_argument("--staged", action="store_true", help="审计 staged diff 而不是 anchor..HEAD")
    parser.add_argument("--change", help="可选：运行 openspec validate 的 change 名")
    parser.add_argument("--no-openspec", action="store_true", help="跳过 OpenSpec 证据")
    parser.add_argument("--pretty", action="store_true", help="格式化 JSON 输出")
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(sys.argv[1:] if argv is None else argv)
    try:
        report, blocking = build_report(args)
    except Exception as exc:
        print(json.dumps({"blocking": ["审计证据生成失败"], "unknowns": [str(exc)]},
                         ensure_ascii=False))
        return 2
    print(json.dumps(report, ensure_ascii=False, indent=2 if args.pretty else None))
    return 1 if blocking else 0


if __name__ == "__main__":
    raise SystemExit(main())
