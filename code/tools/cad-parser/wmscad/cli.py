"""命令行入口：python -m wmscad <子命令>。

  parse    解析图纸，打印空间结构与库位数量
  layout   只画空间时，由系统自动排布货架带
  render   渲染示意图（schematic）或按原始样式（native）
  gen      生成测试与演示图纸
  verify   跑闭环验证
"""

from __future__ import annotations

import argparse
import json
import sys
from dataclasses import asdict
from pathlib import Path

from . import checks, fixtures
from .layout import auto_layout
from .model import ParseResult
from .parser import DEFAULT_RACK_LENGTH, parse
from .render import render_native, render_schematic


def main(argv: list[str] | None = None) -> int:
    parser = _build_parser()
    args = parser.parse_args(argv)
    return args.handler(args)


def _build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(prog="wmscad", description="仓库平面图解析工具")
    sub = parser.add_subparsers(dest="command", required=True)

    p = sub.add_parser("parse", help="解析图纸并打印结构")
    p.add_argument("dxf")
    p.add_argument("--rack-length", type=float, default=DEFAULT_RACK_LENGTH)
    p.add_argument("--json", help="把结果写入 JSON 文件")
    p.set_defaults(handler=_cmd_parse)

    p = sub.add_parser("layout", help="自动排布货架带")
    p.add_argument("dxf")
    p.add_argument("--band-depth", type=float, default=fixtures.BAND_DEPTH)
    p.add_argument("--aisle", type=float, default=fixtures.AISLE_WIDTH)
    p.add_argument("--rack-length", type=float, default=fixtures.RACK_LENGTH)
    p.add_argument("--levels", type=int, default=fixtures.LEVELS)
    p.add_argument("--positions", type=int, default=fixtures.POSITIONS)
    p.set_defaults(handler=_cmd_layout)

    p = sub.add_parser("render", help="渲染图纸")
    p.add_argument("dxf")
    p.add_argument("--out", required=True)
    p.add_argument("--mode", choices=("schematic", "native"), default="schematic")
    p.add_argument("--rack-length", type=float, default=DEFAULT_RACK_LENGTH)
    p.add_argument("--auto", action="store_true", help="图上无货架带时自动排布")
    p.add_argument("--no-bands", action="store_true", help="只画空间，不画货架带")
    p.add_argument("--dpi", type=int, default=110)
    p.set_defaults(handler=_cmd_render)

    p = sub.add_parser("gen", help="生成测试与演示图纸")
    p.add_argument("--kind", choices=("flat", "floor", "l3", "l4"), required=True)
    p.add_argument("--out", required=True)
    p.set_defaults(handler=_cmd_gen)

    p = sub.add_parser("verify", help="跑闭环验证")
    p.set_defaults(handler=_cmd_verify)

    return parser


def _cmd_parse(args) -> int:
    result = parse(Path(args.dxf), args.rack_length)
    print(_format_result(args.dxf, result))
    if args.json:
        Path(args.json).write_text(
            json.dumps(_as_dict(result), ensure_ascii=False, indent=2),
            encoding="utf-8",
        )
        print(f"\nJSON 已写入 {args.json}")
    return 0


def _cmd_layout(args) -> int:
    result = parse(Path(args.dxf), args.rack_length)
    if not result.outline_points:
        print("未找到外轮廓（OUTLINE 图层）", file=sys.stderr)
        return 1

    bands = auto_layout(
        result.outline_points,
        result.obstacles(),
        band_depth=args.band_depth,
        aisle_width=args.aisle,
        rack_length=args.rack_length,
        levels=args.levels,
        positions=args.positions,
    )
    print(f"自动排布：{args.dxf}")
    print(f"  带深 {args.band_depth / 1000:.1f} m  "
          f"通道 {args.aisle / 1000:.1f} m  "
          f"模数 {(args.band_depth + args.aisle) / 1000:.1f} m")
    print("-" * 56)
    for index, band in enumerate(bands, 1):
        r = band.rect
        print(f"  #{index:<3} x {r.x / 1000:6.1f}  y {r.y / 1000:6.1f}  "
              f"长 {r.w / 1000:6.1f} m -> {band.racks} 架 {band.bins} 位")
    print("-" * 56)
    print(f"货架带 {len(bands)} 条  "
          f"货架 {sum(b.racks for b in bands)} 个  "
          f"库位 {sum(b.bins for b in bands)} 个")
    return 0


def _cmd_render(args) -> int:
    if args.mode == "native":
        render_native(Path(args.dxf), Path(args.out), dpi=args.dpi)
        return 0

    result = parse(Path(args.dxf), args.rack_length)
    if args.auto and not result.bands:
        result.bands = auto_layout(
            result.outline_points,
            result.obstacles(),
            band_depth=fixtures.BAND_DEPTH,
            aisle_width=fixtures.AISLE_WIDTH,
            rack_length=fixtures.RACK_LENGTH,
            levels=fixtures.LEVELS,
            positions=fixtures.POSITIONS,
        )
        _assign_zones(result)
        print(f"  自动排布：{len(result.bands)} 条带")
    if args.no_bands:
        result.bands = []

    render_schematic(result, Path(args.out), rack_length=args.rack_length,
                     dpi=args.dpi)
    return 0


def _cmd_gen(args) -> int:
    output = Path(args.out)
    builders = {
        "flat": fixtures.build_flat,
        "floor": fixtures.build_floor,
        "l3": fixtures.build_l3,
        "l4": fixtures.build_l4,
    }
    labels = {"flat": "平面仓", "floor": "楼层仓", "l3": "L3 样本", "l4": "L4 样本"}
    builders[args.kind](output)
    print(f"已生成 {output}  （{labels[args.kind]}）")
    return 0


def _cmd_verify(args) -> int:
    results = checks.run_all()
    print(checks.format_report(results))
    return 0 if all(c.ok for c in results) else 1


# ---------------- 输出格式 ----------------


def _assign_zones(result: ParseResult) -> None:
    """自动排布出的带没有归属，按库区矩形补一次。"""
    for zone in result.zones:
        zone.band_count = 0
        zone.bin_count = 0
    for band in result.bands:
        for zone in result.zones:
            if zone.rect.contains(band.rect.cx, band.rect.cy):
                zone.band_count += 1
                zone.bin_count += band.bins
                band.zone = zone.code
                break


def _format_result(name: str, result: ParseResult) -> str:
    lines = ["=" * 52, f"图纸：{name}", "=" * 52]

    outline = result.outline
    if outline:
        lines.append(
            f"外轮廓    {outline.w / 1000:.1f} m × {outline.h / 1000:.1f} m，"
            f"{len(result.outline_points)} 个顶点，"
            f"实际面积 {result.outline_area / 1e6:.0f} ㎡"
        )
    lines.append(f"柱子      {len(result.columns)} 根")
    lines.append(f"设施      {len(result.facilities)} 处")
    lines.append(f"库区      {len(result.zones)} 个")
    for zone in result.zones:
        if zone.code:
            lines.append(
                f"          {zone.code}：{zone.band_count} 条带，{zone.bin_count} 库位"
            )
    lines.append(f"货架带    {len(result.bands)} 条")
    for index, band in enumerate(result.bands, 1):
        lines.append(
            f"          #{index} {band.spec} "
            f"长 {band.rect.long_side / 1000:.1f} m -> "
            f"{band.racks} 架 {band.bins} 位"
        )
    lines.append("-" * 52)
    lines.append(f"货架总数  {result.rack_count}")
    lines.append(f"库位总数  {result.bin_count}")

    if result.warnings:
        lines.append("-" * 52)
        lines += [f"⚠ {w}" for w in result.warnings]
    return "\n".join(lines)


def _as_dict(result: ParseResult) -> dict:
    return {
        "outline_vertices": len(result.outline_points),
        "outline_area_sqm": round(result.outline_area / 1e6, 1),
        "columns": len(result.columns),
        "facilities": [
            {"layer": f.layer, "name": f.name, "rect": asdict(f.rect)}
            for f in result.facilities
        ],
        "zones": [
            {"code": z.code, "rect": asdict(z.rect),
             "bands": z.band_count, "bins": z.bin_count}
            for z in result.zones
        ],
        "bands": [
            {"rect": asdict(b.rect), "spec": b.spec,
             "racks": b.racks, "bins": b.bins, "zone": b.zone}
            for b in result.bands
        ],
        "racks": result.rack_count,
        "bins": result.bin_count,
        "warnings": result.warnings,
    }
