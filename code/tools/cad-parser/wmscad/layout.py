"""L4 自动排布：由空间约束生成货架带。

扫描线算法，不依赖几何库：
  沿 Y 按「带深 + 通道宽」的模数推进；
  每条带取带顶与带底两处横截面的交集，保证不跨形状边界；
  再用障碍物做区间减法，保证不压柱子与设施；
  最后收缩墙边余量，长度不足最小货架数的碎段丢弃。

柱子落在通道里时不阻断货架带，落在带上时才切段——
这与真实货架设计一致，柱距通常是「带深 + 通道」的整数倍。
"""

from __future__ import annotations

from typing import Sequence

from .geom import Point, Rect, intersect, polygon_x_ranges, subtract
from .model import Band

DEFAULT_BAND_DEPTH = 2200.0
DEFAULT_AISLE_WIDTH = 2000.0
DEFAULT_RACK_LENGTH = 2700.0
DEFAULT_OFFSET = 500.0
DEFAULT_WALL_MARGIN = 500.0
DEFAULT_MIN_RACKS = 3


def auto_layout(
    outline: Sequence[Point],
    obstacles: Sequence[Rect],
    *,
    band_depth: float = DEFAULT_BAND_DEPTH,
    aisle_width: float = DEFAULT_AISLE_WIDTH,
    rack_length: float = DEFAULT_RACK_LENGTH,
    levels: int = 4,
    positions: int = 2,
    offset: float = DEFAULT_OFFSET,
    wall_margin: float = DEFAULT_WALL_MARGIN,
    min_racks: int = DEFAULT_MIN_RACKS,
) -> list[Band]:
    """在轮廓内排布货架带。

    offset 是首条带距轮廓底边的距离，wall_margin 是每条带两端留出的余量。
    两者分开是因为前者受柱网模数约束，后者只受作业空间约束。
    """
    if len(outline) < 3:
        return []

    ys = [p[1] for p in outline]
    y_min, y_max = min(ys), max(ys)
    module = band_depth + aisle_width
    min_length = min_racks * rack_length

    bands: list[Band] = []
    y = y_min + offset
    while y + band_depth <= y_max:
        top, bottom = y, y + band_depth

        upper = polygon_x_ranges(outline, top + 1)
        lower = polygon_x_ranges(outline, bottom - 1)
        segments = intersect(upper, lower)

        if segments:
            blocked = [
                o.x_interval() for o in obstacles if o.y < bottom and o.y2 > top
            ]
            free = subtract(segments, blocked)
            for start, end in free:
                left, right = start + wall_margin, end - wall_margin
                if right - left < min_length:
                    continue
                bands.append(
                    Band(
                        rect=Rect(left, y, right - left, band_depth),
                        levels=levels,
                        positions=positions,
                        rack_length=rack_length,
                    )
                )

        y += module

    return bands
