"""图纸 → 空间事实。

只认六个图层：OUTLINE / COLUMN / FACILITY / DOCK / ZONE / BAND / TEXT。
图层不认识的内容一律忽略，不做猜测——猜错比漏读更难排查。
"""

from __future__ import annotations

import re
from pathlib import Path

from . import reader
from .geom import Rect
from .model import Band, Facility, ParseResult, Text, Zone

SPEC_PATTERN = re.compile(r"^\s*(\d+)\s*[Ll]\s*(\d+)\s*[Pp]\s*$")

OUTLINE_LAYER = "OUTLINE"
COLUMN_LAYER = "COLUMN"
FACILITY_LAYER = "FACILITY"
DOCK_LAYER = "DOCK"
AISLE_LAYER = "AISLE"
ZONE_LAYER = "ZONE"
BAND_LAYER = "BAND"
TEXT_LAYER = "TEXT"

DEFAULT_RACK_LENGTH = 2700.0


def parse_spec(text: str) -> tuple[int, int] | None:
    """解析 `4L2P` 形式的规格标注，返回 (层数, 每层位数)。"""
    match = SPEC_PATTERN.match(text)
    if not match:
        return None
    return int(match.group(1)), int(match.group(2))


def parse(path: Path, rack_length: float = DEFAULT_RACK_LENGTH) -> ParseResult:
    doc = reader.load(path)
    result = ParseResult()

    outlines: list[tuple[Rect, list[tuple[float, float]]]] = []
    zone_rects: list[Rect] = []
    band_rects: list[Rect] = []

    for entity in reader.walk(doc.modelspace()):
        layer = reader.layer_of(entity)
        if layer is None:
            continue

        if layer == TEXT_LAYER:
            text = reader.as_text(entity)
            anchor = reader.text_anchor(entity)
            if text and anchor:
                result.texts.append(Text(text.strip(), anchor[0], anchor[1], layer))
            continue

        rect = reader.as_rect(entity)
        if rect is None:
            continue

        if layer == OUTLINE_LAYER:
            outlines.append((rect, reader.as_polygon(entity)))
        elif layer == COLUMN_LAYER:
            result.columns.append(rect)
        elif layer == AISLE_LAYER:
            result.aisles.append(rect)
        elif layer in (FACILITY_LAYER, DOCK_LAYER):
            result.facilities.append(Facility(layer=layer, rect=rect))
        elif layer == ZONE_LAYER:
            zone_rects.append(rect)
        elif layer == BAND_LAYER:
            band_rects.append(rect)

    _pick_outline(result, outlines)
    labels = _attach_texts(result, zone_rects)
    _build_zones(result, zone_rects, labels)
    _build_bands(result, band_rects, rack_length)
    _assign_bands_to_zones(result)
    _check_zone_overlaps(result)
    return result


def _pick_outline(
    result: ParseResult,
    outlines: list[tuple[Rect, list[tuple[float, float]]]],
) -> None:
    """多个轮廓时取包围盒最大的作为主轮廓，其余保留供渲染使用。"""
    if not outlines:
        result.warnings.append("未找到外轮廓（OUTLINE 图层）")
        return
    largest, points = max(outlines, key=lambda item: item[0].area)
    result.outline_points = points
    result.outline_all = [pts for _, pts in outlines if len(pts) >= 3]
    if len(outlines) > 1:
        result.warnings.append(f"发现 {len(outlines)} 个外轮廓，已取面积最大者")


def _attach_texts(result: ParseResult, zone_rects: list[Rect]) -> dict[Rect, str]:
    """把文字挂到所属的库区或设施上，返回 库区矩形 → 编码 的映射。

    货架带的规格标注（如 4L2P）落在库区范围内，必须排除，
    否则会被当成库区编码。
    """
    labels: dict[Rect, str] = {}
    for text in result.texts:
        if parse_spec(text.text):
            continue
        facility = next(
            (f for f in result.facilities if f.rect.contains(text.x, text.y)), None
        )
        if facility is not None:
            facility.name = text.text
            continue
        rect = next((r for r in zone_rects if r.contains(text.x, text.y)), None)
        if rect is not None:
            labels.setdefault(rect, text.text)
    return labels


def _build_zones(
    result: ParseResult, zone_rects: list[Rect], labels: dict[Rect, str]
) -> None:
    for rect in zone_rects:
        result.zones.append(Zone(rect=rect, code=labels.get(rect)))


def _build_bands(result: ParseResult, band_rects: list[Rect], rack_length: float) -> None:
    for rect in band_rects:
        spec = _find_band_spec(result, rect)
        if spec is None:
            result.warnings.append(
                f"货架带 ({rect.x:.0f},{rect.y:.0f}) 未找到规格标注，已跳过"
            )
            continue
        levels, positions = spec
        result.bands.append(
            Band(rect=rect, levels=levels, positions=positions, rack_length=rack_length)
        )


def _find_band_spec(result: ParseResult, rect: Rect) -> tuple[int, int] | None:
    for text in result.texts:
        spec = parse_spec(text.text)
        if spec and rect.contains(text.x, text.y):
            return spec
    return None


def _assign_bands_to_zones(result: ParseResult) -> None:
    for band in result.bands:
        for zone in result.zones:
            if zone.rect.contains(band.rect.cx, band.rect.cy):
                zone.band_count += 1
                zone.bin_count += band.bins
                band.zone = zone.code
                break


def _check_zone_overlaps(result: ParseResult) -> None:
    zones = result.zones
    for i, left in enumerate(zones):
        for right in zones[i + 1 :]:
            if left.rect.overlaps(right.rect):
                result.warnings.append(
                    f"库区 {left.code or '未命名'} 与 {right.code or '未命名'} 重叠"
                )
