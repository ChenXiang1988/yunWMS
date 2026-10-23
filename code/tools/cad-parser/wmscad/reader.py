"""DXF 读取层。

只负责"把图纸变成实体流"，不关心业务含义。
两个必须记住的坑：
  1. 大部分几何藏在块定义里，只扫模型空间会漏掉九成内容；
  2. 部分实体（代理对象、天正对象）没有 layer 属性，直接访问会抛异常。
"""

from __future__ import annotations

from pathlib import Path
from typing import Iterable, Iterator

import ezdxf
from ezdxf.entities import DXFEntity

from .geom import Rect

MAX_BLOCK_DEPTH = 5


class DrawingError(RuntimeError):
    """图纸无法读取时抛出。"""


def load(path: Path) -> ezdxf.document.Drawing:
    try:
        return ezdxf.readfile(str(path))
    except FileNotFoundError as exc:
        raise DrawingError(f"文件不存在：{path}") from exc
    except ezdxf.DXFStructureError as exc:
        raise DrawingError(f"图纸结构损坏：{exc}") from exc


def walk(entities: Iterable[DXFEntity], depth: int = 0) -> Iterator[DXFEntity]:
    """深度优先遍历，遇到块引用递归展开。"""
    for entity in entities:
        if entity.dxftype() == "INSERT" and depth < MAX_BLOCK_DEPTH:
            try:
                yield from walk(entity.virtual_entities(), depth + 1)
            except Exception:
                # 块损坏或循环引用时跳过，不影响其余实体
                continue
        else:
            yield entity


def layer_of(entity: DXFEntity) -> str | None:
    try:
        return entity.dxf.layer
    except Exception:
        return None


def as_rect(entity: DXFEntity) -> Rect | None:
    """闭合多段线或圆的外接矩形。"""
    kind = entity.dxftype()
    if kind == "LWPOLYLINE":
        points = list(entity.get_points("xy"))
        if len(points) < 3:
            return None
        xs = [p[0] for p in points]
        ys = [p[1] for p in points]
        return Rect(min(xs), min(ys), max(xs) - min(xs), max(ys) - min(ys))
    if kind == "CIRCLE":
        center, radius = entity.dxf.center, entity.dxf.radius
        return Rect(center.x - radius, center.y - radius, 2 * radius, 2 * radius)
    return None


def as_polygon(entity: DXFEntity) -> list[tuple[float, float]]:
    """闭合多段线的顶点序列；其它实体返回空列表。"""
    if entity.dxftype() != "LWPOLYLINE":
        return []
    return [(p[0], p[1]) for p in entity.get_points("xy")]


def as_text(entity: DXFEntity) -> str | None:
    kind = entity.dxftype()
    if kind == "TEXT":
        return entity.dxf.text
    if kind == "MTEXT":
        return entity.plain_text()
    return None


def text_anchor(entity: DXFEntity) -> tuple[float, float] | None:
    if entity.dxftype() not in ("TEXT", "MTEXT"):
        return None
    try:
        point = entity.dxf.insert
    except Exception:
        return None
    return (point.x, point.y)
