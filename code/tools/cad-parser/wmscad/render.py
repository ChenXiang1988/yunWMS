"""两种渲染：

  schematic  把解析结果画成带标注的示意图，给人核对结构与数量。
  native     按 CAD 原始样式渲染，用来验证"提取出来的图和原图是否一致"。
"""

from __future__ import annotations

from pathlib import Path

import matplotlib

matplotlib.use("Agg")

import matplotlib.pyplot as plt
from matplotlib.lines import Line2D
from matplotlib.patches import Polygon, Rectangle

import ezdxf
from ezdxf.addons.drawing import Frontend, RenderContext
from ezdxf.addons.drawing.matplotlib import MatplotlibBackend

from .geom import Rect
from .model import ParseResult
from .parser import parse_spec

C_OUTLINE = "#1f2329"
C_BAND = "#2b6de5"
C_RACK = "#7ba7f0"
C_COLUMN = "#d92d20"
C_ZONE = "#12925a"
C_DOCK = "#d97706"
C_FACILITY = "#8c8c8c"
C_AISLE = "#bfbfbf"

CJK_FONTS = ["PingFang SC", "Heiti SC", "Arial Unicode MS", "DejaVu Sans"]


def _setup_fonts() -> None:
    plt.rcParams["font.sans-serif"] = CJK_FONTS
    plt.rcParams["axes.unicode_minus"] = False


def render_schematic(
    result: ParseResult,
    out: Path,
    *,
    rack_length: float = 2_700.0,
    title: str | None = None,
    dpi: int = 110,
) -> None:
    if not result.outline_points:
        raise ValueError("未找到外轮廓（OUTLINE 图层）")

    _setup_fonts()
    fig, ax = plt.subplots(figsize=(16, 9), dpi=dpi)
    ax.set_aspect("equal")
    ax.axis("off")

    _draw_outline(ax, result)
    _draw_zones(ax, result)
    _draw_bands(ax, result)
    _draw_columns(ax, result)
    _draw_facilities(ax, result)
    _draw_aisles(ax, result)
    _draw_loose_texts(ax, result)
    _draw_band_annotation(ax, result, rack_length)
    _draw_aisle_annotation(ax, result)

    x0, x1, y0, y1 = _content_bounds(result)
    ax.set_xlim(x0, x1)
    ax.set_ylim(y0, y1)

    caption = title or _caption(result)
    ax.text(x0 + 1_000, y1 - 2_500, caption,
            fontsize=13, color=C_OUTLINE, fontweight="bold", va="top")

    _draw_legend(ax)
    _draw_stats(ax, result)
    _draw_column_callout(ax, result)

    fig.subplots_adjust(left=0.02, right=0.82, top=0.96, bottom=0.05)
    out.parent.mkdir(parents=True, exist_ok=True)
    fig.savefig(out, facecolor="white")
    plt.close(fig)
    print(f"已输出 {out}")


# ---------------- 各部分 ----------------


def _draw_outline(ax, result: ParseResult) -> None:
    for points in result.outline_all or [result.outline_points]:
        if len(points) >= 3:
            ax.add_patch(
                Polygon(points, closed=True, fill=False, lw=3, ec=C_OUTLINE, zorder=2)
            )


def _draw_zones(ax, result: ParseResult) -> None:
    for zone in result.zones:
        r = zone.rect
        ax.add_patch(
            Rectangle((r.x, r.y), r.w, r.h, fill=False, ls=(0, (6, 4)),
                      lw=2, ec=C_ZONE, zorder=2)
        )
        label = zone.code or "未命名"
        detail = f"{zone.band_count} 条带 · {zone.bin_count} 库位"
        ax.text(r.x + 800, r.y2 - 400, f"{label} 区 · {detail}",
                fontsize=11, color=C_ZONE, fontweight="bold", va="top", zorder=9,
                bbox=dict(fc="white", ec="none", pad=1.5, alpha=0.9))


def _draw_bands(ax, result: ParseResult) -> None:
    for index, band in enumerate(result.bands, 1):
        r = band.rect
        ax.add_patch(
            Rectangle((r.x, r.y), r.w, r.h, fc=C_BAND, alpha=0.16,
                      ec=C_BAND, lw=1.4, zorder=3)
        )
        _draw_rack_dividers(ax, r, band.racks)
        ax.text(r.x + r.w / 2, r.y + r.h / 2,
                f"#{index}  {band.spec} = {band.racks}架 {band.bins}位",
                ha="center", va="center", fontsize=8.5, color="#0d3f8f", zorder=5)


def _draw_rack_dividers(ax, rect: Rect, racks: int) -> None:
    """在带内画出每个货架的分界，让人看清一条带等于几个货架。"""
    if racks < 2:
        return
    horizontal = rect.w >= rect.h
    step = (rect.w if horizontal else rect.h) / racks
    for k in range(1, racks):
        if horizontal:
            x = rect.x + k * step
            ax.plot([x, x], [rect.y, rect.y2], color=C_RACK, lw=0.7, zorder=4)
        else:
            y = rect.y + k * step
            ax.plot([rect.x, rect.x2], [y, y], color=C_RACK, lw=0.7, zorder=4)


def _draw_columns(ax, result: ParseResult) -> None:
    for column in result.columns:
        ax.add_patch(
            Rectangle((column.x, column.y), column.w, column.h,
                      fc=C_COLUMN, ec="none", zorder=6)
        )


def _draw_facilities(ax, result: ParseResult) -> None:
    for facility in result.facilities:
        r = facility.rect
        if facility.is_dock:
            # 不透明填充压住墙线，体现此处是开口
            ax.add_patch(Rectangle((r.x, r.y), r.w, r.h, fc=C_DOCK, ec="#8a4b00",
                                   lw=1.2, zorder=5))
            color = "white"
        else:
            ax.add_patch(Rectangle((r.x, r.y), r.w, r.h, fc=C_FACILITY, alpha=0.35,
                                   ec=C_FACILITY, lw=1, zorder=5))
            color = "#333"
        if facility.name:
            ax.text(r.cx, r.cy, facility.name, ha="center", va="center",
                    fontsize=9, color=color, zorder=10,
                    bbox=dict(fc="white", ec="none", pad=0.8, alpha=0.75))


def _draw_aisles(ax, result: ParseResult) -> None:
    for aisle in result.aisles:
        ax.add_patch(Rectangle((aisle.x, aisle.y), aisle.w, aisle.h,
                               fc=C_AISLE, alpha=0.35, ec=C_AISLE, lw=1, zorder=1))
        ax.text(aisle.cx, aisle.y + 2_000, "主干通道", ha="center", va="bottom",
                fontsize=9, color="#666", zorder=2)


def _draw_loose_texts(ax, result: ParseResult) -> None:
    """层号、图名等散落文字，排除已随设施、库区、货架带画过的。"""
    for text in result.texts:
        if parse_spec(text.text):
            continue
        if any(f.rect.contains(text.x, text.y) for f in result.facilities):
            continue
        if any(z.rect.contains(text.x, text.y) for z in result.zones):
            continue
        ax.text(text.x, text.y, text.text, fontsize=11, color="#555",
                ha="center", va="center", zorder=8)


def _draw_band_annotation(ax, result: ParseResult, rack_length: float) -> None:
    if not result.bands:
        return
    first = result.bands[0]
    r = first.rect
    y = r.y - 5_000
    ax.annotate("", xy=(r.x, y), xytext=(r.x2, y),
                arrowprops=dict(arrowstyle="<->", color=C_BAND, lw=1))
    ax.text(r.cx, y - 1_800,
            f"带长 {r.w / 1000:.1f} m ÷ 货架宽 {rack_length / 1000:.1f} m "
            f"= {first.racks} 个货架",
            ha="center", fontsize=10, color=C_BAND)


def _draw_aisle_annotation(ax, result: ParseResult) -> None:
    if len(result.bands) < 2:
        return
    first, second = result.bands[0].rect, result.bands[1].rect
    x = first.x + first.w * 0.62
    ax.annotate("", xy=(x, first.y2), xytext=(x, second.y),
                arrowprops=dict(arrowstyle="<->", color="#666", lw=1.0))
    ax.text(x + 1_500, (first.y2 + second.y) / 2,
            f"通道 {abs(second.y - first.y2) / 1000:.1f} m",
            fontsize=8.5, color="#555", va="center",
            bbox=dict(fc="white", ec="none", pad=0.8, alpha=0.85))


def _draw_column_callout(ax, result: ParseResult) -> None:
    if not result.columns:
        return
    column = result.columns[0]
    ax.annotate(
        f"柱 {column.w / 1000:.1f}×{column.h / 1000:.1f} m",
        xy=(column.cx, column.cy),
        xytext=(column.cx + 12_000, column.cy + 9_000),
        fontsize=9.5, color=C_COLUMN,
        arrowprops=dict(arrowstyle="-", color=C_COLUMN, lw=1),
        bbox=dict(fc="white", ec=C_COLUMN, lw=0.8, pad=1.5),
        zorder=12,
    )


def _draw_legend(ax) -> None:
    handles = [
        Line2D([0], [0], color=C_OUTLINE, lw=3, label="外轮廓"),
        Line2D([0], [0], marker="s", color="none", markerfacecolor=C_BAND,
               markersize=12, alpha=0.4, label="货架带"),
        Line2D([0], [0], marker="s", color="none", markerfacecolor=C_COLUMN,
               markersize=10, label="柱子"),
        Line2D([0], [0], color=C_ZONE, lw=2, ls=(0, (6, 4)), label="库区"),
        Line2D([0], [0], marker="s", color="none", markerfacecolor=C_DOCK,
               markersize=12, label="月台"),
        Line2D([0], [0], marker="s", color="none", markerfacecolor=C_FACILITY,
               markersize=12, alpha=0.5, label="设施"),
    ]
    ax.legend(handles=handles, loc="upper left", bbox_to_anchor=(1.01, 1.0),
              frameon=False, fontsize=10)


def _draw_stats(ax, result: ParseResult) -> None:
    lines = [
        f"外轮廓   {len(result.outline_all) or 1} 个",
        f"面积     {result.outline_area / 1e6:.0f} ㎡",
    ]
    if result.columns:
        first = result.columns[0]
        lines.append(f"柱截面   {first.w / 1000:.1f} × {first.h / 1000:.1f} m")
        pitch = _median_pitch(result.columns)
        if pitch:
            lines.append(f"柱网     {pitch / 1000:.1f} m")
        lines.append(f"柱子     {len(result.columns)} 根")
    lines += [
        f"设施     {len(result.facilities)} 处",
        f"货架带   {len(result.bands)} 条",
        f"货架     {result.rack_count} 个",
        f"库位     {result.bin_count} 个",
        f"库区     {len(result.zones)} 个",
    ]
    ax.text(1.01, 0.58, "\n".join(lines), transform=ax.transAxes,
            fontsize=11, va="top", linespacing=1.85)


def _median_pitch(columns: list[Rect]) -> float | None:
    xs = sorted({round(c.cx) for c in columns})
    if len(xs) < 2:
        return None
    gaps = sorted(xs[i + 1] - xs[i] for i in range(len(xs) - 1))
    return gaps[len(gaps) // 2]


def _content_bounds(result: ParseResult) -> tuple[float, float, float, float]:
    xs: list[float] = []
    ys: list[float] = []
    for points in result.outline_all or [result.outline_points]:
        xs += [p[0] for p in points]
        ys += [p[1] for p in points]
    for rect in [*result.columns, *(f.rect for f in result.facilities)]:
        xs += [rect.x, rect.x2]
        ys += [rect.y, rect.y2]
    for text in result.texts:
        xs.append(text.x)
        ys.append(text.y)
    if not xs:
        return (-1_000, 1_000, -1_000, 1_000)
    return (min(xs) - 4_000, max(xs) + 4_000, min(ys) - 9_000, max(ys) + 12_000)


def _caption(result: ParseResult) -> str:
    outline = result.outline
    if outline is None:
        return "仓库平面图"
    area = result.outline_area / 1e6
    if len(result.outline_all) > 1:
        return f"仓库外轮廓  {len(result.outline_all)} 套平面  合计 {area:.0f} ㎡"
    return (f"仓库外轮廓  {len(result.outline_points)} 边形  "
            f"{outline.w / 1000:.1f} m × {outline.h / 1000:.1f} m  约 {area:.0f} ㎡")


# ---------------- 原样渲染 ----------------


def render_native(dxf_path: Path, out: Path, *, dpi: int = 120) -> None:
    doc = ezdxf.readfile(str(dxf_path))
    msp = doc.modelspace()

    fig = plt.figure(figsize=(16, 12), dpi=dpi)
    ax = fig.add_axes([0.02, 0.02, 0.96, 0.96])
    ax.set_axis_off()

    backend = MatplotlibBackend(ax)
    Frontend(RenderContext(doc), backend).draw_layout(msp, finalize=True)

    out.parent.mkdir(parents=True, exist_ok=True)
    fig.savefig(out, facecolor="white", bbox_inches="tight")
    plt.close(fig)
    print(f"已输出 {out}  实体 {len(msp)} 个")
