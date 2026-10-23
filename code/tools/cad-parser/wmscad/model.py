"""解析结果的数据结构。

约定：库位条数、库位总数等派生值一律用 property 计算，
不提供 expand() 这类"必须先调用"的方法——调用顺序错了不会报错，
但结果会静默为 0，这类坑不值得留。
"""

from __future__ import annotations

from dataclasses import dataclass, field

from .geom import Rect, polygon_area


@dataclass
class Text:
    """图纸上的文字，带插入点。"""

    text: str
    x: float
    y: float
    layer: str = "TEXT"


@dataclass
class Facility:
    """设施与月台：楼梯、货梯、卫生间、办公区、月台。不承载库存。"""

    layer: str
    rect: Rect
    name: str | None = None

    @property
    def is_dock(self) -> bool:
        return self.layer == "DOCK"


@dataclass
class Zone:
    """库区。边界来自图纸，用途等业务属性在系统中配置。"""

    rect: Rect
    code: str | None = None
    band_count: int = 0
    bin_count: int = 0


@dataclass
class Band:
    """货架带：一排背靠背货架。带长除以单排货架宽即为货架数。"""

    rect: Rect
    levels: int
    positions: int
    rack_length: float
    zone: str | None = None

    @property
    def racks(self) -> int:
        return int(self.rect.long_side // self.rack_length)

    @property
    def bins(self) -> int:
        return self.racks * self.levels * self.positions

    @property
    def spec(self) -> str:
        return f"{self.levels}L{self.positions}P"


@dataclass
class ParseResult:
    """一张图纸解析后的全部空间事实。"""

    outline_points: list[tuple[float, float]] = field(default_factory=list)
    outline_all: list[list[tuple[float, float]]] = field(default_factory=list)
    columns: list[Rect] = field(default_factory=list)
    facilities: list[Facility] = field(default_factory=list)
    aisles: list[Rect] = field(default_factory=list)
    zones: list[Zone] = field(default_factory=list)
    bands: list[Band] = field(default_factory=list)
    texts: list[Text] = field(default_factory=list)
    warnings: list[str] = field(default_factory=list)

    @property
    def outline(self) -> Rect | None:
        """外轮廓的包围盒；没有轮廓时为 None。"""
        if not self.outline_points:
            return None
        xs = [p[0] for p in self.outline_points]
        ys = [p[1] for p in self.outline_points]
        return Rect(min(xs), min(ys), max(xs) - min(xs), max(ys) - min(ys))

    @property
    def outline_area(self) -> float:
        if len(self.outline_points) < 3:
            return 0.0
        return polygon_area(self.outline_points)

    @property
    def docks(self) -> list[Facility]:
        return [f for f in self.facilities if f.is_dock]

    @property
    def rack_count(self) -> int:
        return sum(b.racks for b in self.bands)

    @property
    def bin_count(self) -> int:
        return sum(b.bins for b in self.bands)

    def obstacles(self) -> list[Rect]:
        """排布时需要避开的实体：柱子与设施。"""
        return [*self.columns, *(f.rect for f in self.facilities)]
