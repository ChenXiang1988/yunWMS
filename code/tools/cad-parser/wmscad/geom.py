"""平面几何工具。

坐标统一为图纸单位（毫米），原点在图纸左下角。
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Iterable, Sequence

Point = tuple[float, float]
Interval = tuple[float, float]


@dataclass(frozen=True)
class Rect:
    """轴对齐矩形。所有库位、货架带、障碍物都用它描述。"""

    x: float
    y: float
    w: float
    h: float

    @property
    def x2(self) -> float:
        return self.x + self.w

    @property
    def y2(self) -> float:
        return self.y + self.h

    @property
    def cx(self) -> float:
        return self.x + self.w / 2

    @property
    def cy(self) -> float:
        return self.y + self.h / 2

    @property
    def long_side(self) -> float:
        return max(self.w, self.h)

    @property
    def short_side(self) -> float:
        return min(self.w, self.h)

    @property
    def area(self) -> float:
        return self.w * self.h

    def contains(self, px: float, py: float, margin: float = 0.0) -> bool:
        return (
            self.x - margin <= px <= self.x2 + margin
            and self.y - margin <= py <= self.y2 + margin
        )

    def overlaps(self, other: Rect) -> bool:
        return (
            self.x < other.x2
            and other.x < self.x2
            and self.y < other.y2
            and other.y < self.y2
        )

    def x_interval(self) -> Interval:
        return (self.x, self.x2)


def polygon_area(points: Sequence[Point]) -> float:
    """鞋带公式，返回绝对值（不关心绕向）。"""
    total = 0.0
    for i, (x1, y1) in enumerate(points):
        x2, y2 = points[(i + 1) % len(points)]
        total += x1 * y2 - x2 * y1
    return abs(total) / 2


def polygon_x_ranges(points: Sequence[Point], y: float) -> list[Interval]:
    """多边形在给定高度处的横向区间，用于扫描线排布。"""
    xs: list[float] = []
    n = len(points)
    for i in range(n):
        x1, y1 = points[i]
        x2, y2 = points[(i + 1) % n]
        if y1 == y2:
            continue
        lo, hi = (y1, y2) if y1 < y2 else (y2, y1)
        if lo <= y < hi:
            xs.append(x1 + (y - y1) / (y2 - y1) * (x2 - x1))
    xs.sort()
    return [(xs[i], xs[i + 1]) for i in range(0, len(xs) - 1, 2)]


def intersect(a: Iterable[Interval], b: Iterable[Interval]) -> list[Interval]:
    """两组区间的交集。"""
    out: list[Interval] = []
    for s1, e1 in a:
        for s2, e2 in b:
            s, e = max(s1, s2), min(e1, e2)
            if e > s:
                out.append((s, e))
    return out


def subtract(intervals: Iterable[Interval], blocked: Iterable[Interval]) -> list[Interval]:
    """从区间集合中扣除被障碍占用的部分。"""
    result: list[Interval] = []
    for a, b in intervals:
        segments = [(a, b)]
        for c, d in blocked:
            nxt: list[Interval] = []
            for s, e in segments:
                if d <= s or c >= e:
                    nxt.append((s, e))
                    continue
                if s < c:
                    nxt.append((s, c))
                if e > d:
                    nxt.append((d, e))
            segments = nxt
            if not segments:
                break
        result.extend(segments)
    return result


def point_in_polygon(px: float, py: float, points: Sequence[Point]) -> bool:
    """射线法判断点是否落在多边形内。"""
    inside = False
    n = len(points)
    j = n - 1
    for i in range(n):
        xi, yi = points[i]
        xj, yj = points[j]
        if (yi > py) != (yj > py):
            if px < (xj - xi) * (py - yi) / (yj - yi) + xi:
                inside = not inside
        j = i
    return inside


def polygon_corners(rect: Rect) -> list[Point]:
    return [(rect.x, rect.y), (rect.x2, rect.y), (rect.x2, rect.y2), (rect.x, rect.y2)]
