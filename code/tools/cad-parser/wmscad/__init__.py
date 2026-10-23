"""仓库平面图解析工具。

对外只暴露四个入口：解析、排布、渲染、生成样本。
"""

from .geom import Rect
from .layout import auto_layout
from .model import Band, Facility, ParseResult, Zone
from .parser import parse, parse_spec
from .reader import DrawingError

__all__ = [
    "Band",
    "DrawingError",
    "Facility",
    "ParseResult",
    "Rect",
    "Zone",
    "auto_layout",
    "parse",
    "parse_spec",
]
