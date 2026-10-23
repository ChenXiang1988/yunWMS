"""闭环验证。

L3  图纸上的货架带 → 展开成货架与库位 → 与期望值逐项比对
L4  只有空间的图纸 → 自动排布 → 校验性质（不越界、不压障碍、可展开）

性质校验比数字比对更重要：数字只能证明这一次算对了，
性质能证明算法在任何图上都不会产出非法结果。
"""

from __future__ import annotations

import tempfile
from dataclasses import dataclass
from pathlib import Path

import ezdxf

from . import fixtures
from .geom import point_in_polygon
from .layout import auto_layout
from .model import ParseResult
from .parser import parse


@dataclass
class Check:
    name: str
    ok: bool
    detail: str = ""


def run_all() -> list[Check]:
    with tempfile.TemporaryDirectory() as tmpdir:
        tmp = Path(tmpdir)
        checks = _check_l3(tmp)
        checks += _check_l4(tmp)
    return checks


# ---------------- L3 ----------------


def _check_l3(tmp: Path) -> list[Check]:
    expected = fixtures.expected_counts()
    path = tmp / "sample_l3.dxf"
    fixtures.build_l3(path)
    result = parse(path, fixtures.RACK_LENGTH)

    lower = [b for b in result.bands if b.zone == "A"]
    upper = [b for b in result.bands if b.zone == "B"]

    checks = [
        _eq("外轮廓顶点数", len(result.outline_points), len(fixtures.SHAPE_OUTLINE)),
        _eq("柱子数量", len(result.columns), expected["columns"]),
        _eq("库区数量", len(result.zones), 2),
        _eq("货架带数量", len(result.bands), expected["bands"]),
        _eq("A 区带数", len(lower), expected["lower_bands"]),
        _eq("B 区带数", len(upper), expected["upper_bands"]),
        _eq("A 区每带货架数", sorted({b.racks for b in lower}),
            [expected["racks_per_lower"]]),
        _eq("B 区每带货架数", sorted({b.racks for b in upper}),
            [expected["racks_per_upper"]]),
        _eq("货架总数", result.rack_count, expected["racks"]),
        _eq("库位总数", result.bin_count, expected["bins"]),
        _eq("A 区库位", sum(b.bins for b in lower), expected["lower_bins"]),
        _eq("B 区库位", sum(b.bins for b in upper), expected["upper_bins"]),
        _eq("带都已归属库区", all(b.zone for b in result.bands), True),
        _eq("无解析告警", result.warnings, []),
    ]

    checks += _check_missing_spec(path, expected["bands"])
    return checks


def _check_missing_spec(path: Path, expected_bands: int) -> list[Check]:
    """删掉一条带的规格标注，该带应被跳过并告警，而不是瞎猜。"""
    doc = ezdxf.readfile(str(path))
    msp = doc.modelspace()
    for entity in list(msp):
        if entity.dxftype() == "TEXT" and entity.dxf.text.endswith("P"):
            msp.delete_entity(entity)
            break

    broken = path.with_name("broken.dxf")
    doc.saveas(broken)
    result = parse(broken, fixtures.RACK_LENGTH)
    return [
        _eq("缺规格的带被跳过", len(result.bands), expected_bands - 1),
        _eq("缺规格产生告警", len(result.warnings) >= 1, True),
    ]


# ---------------- L4 ----------------


def _check_l4(tmp: Path) -> list[Check]:
    path = tmp / "sample_l4.dxf"
    fixtures.build_l4(path)
    result = parse(path, fixtures.RACK_LENGTH)

    checks = [
        _eq("L4 图纸不含货架带", len(result.bands), 0),
        _eq("L4 图纸有外轮廓", len(result.outline_points), len(fixtures.SHAPE_OUTLINE)),
        _eq("L4 图纸有柱子", len(result.columns) > 0, True),
    ]

    bands = auto_layout(
        result.outline_points,
        result.obstacles(),
        band_depth=fixtures.BAND_DEPTH,
        aisle_width=fixtures.AISLE_WIDTH,
        rack_length=fixtures.RACK_LENGTH,
        levels=fixtures.LEVELS,
        positions=fixtures.POSITIONS,
    )

    checks.append(_eq("排布出带", len(bands) > 0, True))
    if not bands:
        return checks

    checks += [
        _eq("全部带完整落在轮廓内", _all_inside(bands, result), True),
        _eq("没有带压到柱子或设施", _any_clash(bands, result), False),
        _eq("每条带至少 3 个货架", min(b.racks for b in bands) >= 3, True, 
            f"最少 {min(b.racks for b in bands)} 架"),
        _eq("排布结果可展开为库位", sum(b.bins for b in bands) > 0, True,
            f"{sum(b.racks for b in bands)} 架 / {sum(b.bins for b in bands)} 位"),
    ]
    return checks


def _all_inside(bands, result: ParseResult) -> bool:
    depth = fixtures.BAND_DEPTH
    for band in bands:
        r = band.rect
        corners = [(r.x, r.y), (r.x2, r.y), (r.x2, r.y2), (r.x, r.y2)]
        if not all(point_in_polygon(x, y, result.outline_points) for x, y in corners):
            return False
    return True


def _any_clash(bands, result: ParseResult) -> bool:
    obstacles = result.obstacles()
    return any(band.rect.overlaps(o) for band in bands for o in obstacles)


# ---------------- 工具 ----------------


def _eq(name: str, actual, expected, detail: str = "") -> Check:
    ok = actual == expected
    text = detail or f"实际 {actual} / 期望 {expected}"
    return Check(name, ok, text)


def format_report(checks: list[Check]) -> str:
    width = max(len(c.name) for c in checks)
    passed = sum(c.ok for c in checks)
    lines = ["闭环验证", "-" * (width + 30)]
    for check in checks:
        mark = "✅" if check.ok else "❌"
        lines.append(f"{mark}  {check.name:<{width}}  {check.detail}")
    lines.append("-" * (width + 30))
    lines.append(f"{passed}/{len(checks)} 通过")
    return "\n".join(lines)
