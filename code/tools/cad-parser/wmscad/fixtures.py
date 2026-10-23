"""测试与演示图纸生成。

两组样本，用途不同：

  交付样例  平面仓、楼层仓。只画空间事实，用来给客户成功团队看"该交什么"。
  L 形样本  L3（含货架带）与 L4（纯空间）。用来做闭环验证。

几何参数取自真实图纸 `1007晧物仓.dxf` 实测数据，
但那是建筑装修图，没有货架，也没有规则柱网——所以货架带由本工具补。
"""

from __future__ import annotations

from pathlib import Path

import ezdxf
from ezdxf.enums import TextEntityAlignment

M = 1_000  # 米 → 毫米

LAYERS: dict[str, int] = {
    "OUTLINE": 5,
    "COLUMN": 8,
    "FACILITY": 3,
    "DOCK": 3,
    "AISLE": 2,
    "ZONE": 4,
    "BAND": 7,
    "TEXT": 7,
}

# ---- 交付样例的实测参数 ----
# 注意：148.6 × 105.1 是 A0 图框（1:125），不是楼板；真实平面为 123.6 × 68.8
PLATE_W = 123.6 * M
PLATE_H = 68.8 * M
COLUMN_PITCH = 8.3 * M
COLUMN_SIZE = 1.0 * M
FLOOR_GAP = 25 * M

FLAT_FACILITIES = [
    ("办公区", 86, 42, 24, 16),
    ("充电区", 86, 26, 14, 12),
    ("卫生间", 64, 54, 10, 9),
    ("货梯", 52, 54, 8, 9),
    ("楼梯", 42, 54, 7, 9),
]
DOCK_POSITIONS = (12, 46, 80)
DOCK_WIDTH = 12 * M
DOCK_DEPTH = 3_000  # 向墙内伸 0.6 m，视觉上把墙断开

# ---- L 形验证样本参数 ----
SHAPE_OUTLINE = [(0, 0), (150, 0), (150, 60), (95, 60), (95, 105), (0, 105)]
SHAPE_SPLIT_Y = 60
SHAPE_UPPER_WIDTH = 95

SHAPE_COLUMN_PITCH = 8_400
SHAPE_COLUMN_SIZE = 1_000
BAND_DEPTH = 2_200
AISLE_WIDTH = 2_000
MODULE = BAND_DEPTH + AISLE_WIDTH
RACK_LENGTH = 2_700
LEVELS = 4
POSITIONS = 2
BAND_SPEC = f"{LEVELS}L{POSITIONS}P"

LOWER_BAND_Y0 = 0.5
LOWER_BAND_COUNT = 14
LOWER_BAND_X0, LOWER_BAND_X1 = 5, 145

UPPER_BAND_Y0 = SHAPE_SPLIT_Y + 0.5
UPPER_BAND_COUNT = 10
UPPER_BAND_X0, UPPER_BAND_X1 = 5, 55

SHAPE_FACILITIES = [
    ("办公区", 62, 84, 25, 16),
    ("楼梯", 62, 68, 8, 8),
    ("卫生间", 74, 68, 10, 8),
    ("充电区", 88, 68, 6, 8),
]
SHAPE_DOCKS = [("月台1", 20), ("月台2", 60), ("月台3", 100)]


# ---------------- 绘图基础 ----------------


def _new_doc() -> ezdxf.document.Drawing:
    doc = ezdxf.new("R2018")
    doc.header["$INSUNITS"] = 4  # 毫米
    for name, color in LAYERS.items():
        doc.layers.add(name, color=color)
    return doc


def _column_block(doc: ezdxf.document.Drawing, size: float) -> str:
    """柱子用命名块，块名即尺寸，解析时可读可不读。"""
    name = f"COL-{size:.0f}x{size:.0f}"
    if name not in doc.blocks:
        block = doc.blocks.new(name=name)
        half = size / 2
        block.add_lwpolyline(
            [(-half, -half), (half, -half), (half, half), (-half, half)],
            close=True,
            dxfattribs={"layer": "COLUMN"},
        )
    return name


def _rect(msp, x: float, y: float, w: float, h: float, layer: str) -> None:
    msp.add_lwpolyline(
        [(x, y), (x + w, y), (x + w, y + h), (x, y + h)],
        close=True,
        dxfattribs={"layer": layer},
    )


def _label(msp, text: str, x: float, y: float, height: float = 800) -> None:
    entity = msp.add_text(text, height=height, dxfattribs={"layer": "TEXT"})
    entity.set_placement((x, y), align=TextEntityAlignment.MIDDLE_CENTER)


def _grid_columns(msp, ox: float, oy: float, w: float, h: float,
                  pitch: float, size: float) -> None:
    block = f"COL-{size:.0f}x{size:.0f}"
    nx = int((w - pitch) // pitch)
    ny = int((h - pitch) // pitch)
    for i in range(nx + 1):
        for j in range(ny + 1):
            cx, cy = ox + pitch + i * pitch, oy + pitch + j * pitch
            if cx > ox + w - pitch / 2 or cy > oy + h - pitch / 2:
                continue
            msp.add_blockref(block, (cx, cy), dxfattribs={"layer": "COLUMN"})


# ---------------- 交付样例：平面仓 ----------------


def build_flat(path: Path) -> None:
    doc = _new_doc()
    msp = doc.modelspace()
    _column_block(doc, COLUMN_SIZE)

    _rect(msp, 0, 0, PLATE_W, PLATE_H, "OUTLINE")
    _grid_columns(msp, 0, 0, PLATE_W, PLATE_H, COLUMN_PITCH, COLUMN_SIZE)

    for idx, x in enumerate(DOCK_POSITIONS, 1):
        _rect(msp, x * M, -2_400, DOCK_WIDTH, DOCK_DEPTH, "DOCK")
        _label(msp, f"月台{idx}", x * M + DOCK_WIDTH / 2, -1_000, 600)

    for name, x, y, w, h in FLAT_FACILITIES:
        _rect(msp, x * M, y * M, w * M, h * M, "FACILITY")
        _label(msp, name, (x + w / 2) * M, (y + h / 2) * M, 750)

    _label(
        msp,
        f"平面仓  {PLATE_W / M:.1f} m × {PLATE_H / M:.1f} m",
        PLATE_W / 2,
        PLATE_H + 5_000,
        1_600,
    )
    _save(doc, path)


# ---------------- 交付样例：楼层仓 ----------------


def build_floor(path: Path) -> None:
    doc = _new_doc()
    msp = doc.modelspace()
    _column_block(doc, COLUMN_SIZE)

    _one_floor(msp, 0, 0, "F1 一层", with_dock=True)
    _one_floor(msp, PLATE_W + FLOOR_GAP, 0, "F2 二层", with_dock=False)

    _label(
        msp,
        f"楼层仓  {PLATE_W / M:.1f} m × {PLATE_H / M:.1f} m × 2 层",
        PLATE_W + FLOOR_GAP / 2,
        PLATE_H + 8_000,
        1_600,
    )
    _save(doc, path)


def _one_floor(msp, ox: float, oy: float, name: str, with_dock: bool) -> None:
    _rect(msp, ox, oy, PLATE_W, PLATE_H, "OUTLINE")
    _grid_columns(msp, ox, oy, PLATE_W, PLATE_H, COLUMN_PITCH, COLUMN_SIZE)
    _label(msp, name, ox + 22_000, oy + PLATE_H - 8_000, 1_600)

    if with_dock:
        for idx, x in enumerate(DOCK_POSITIONS, 1):
            _rect(msp, ox + x * M, oy - 2_400, DOCK_WIDTH, DOCK_DEPTH, "DOCK")
            _label(msp, f"月台{idx}", ox + x * M + DOCK_WIDTH / 2, oy - 1_000, 600)

    for label, x, y, w, h in FLAT_FACILITIES:
        _rect(msp, ox + x * M, oy + y * M, w * M, h * M, "FACILITY")
        _label(msp, label, ox + (x + w / 2) * M, oy + (y + h / 2) * M, 750)


# ---------------- L 形样本：L3 / L4 ----------------


def _shape_inside(x_m: float, y_m: float) -> bool:
    if not (0 <= x_m <= 150 and 0 <= y_m <= 105):
        return False
    return not (y_m > SHAPE_SPLIT_Y and x_m > SHAPE_UPPER_WIDTH)


def shape_bands() -> list[tuple[float, float, float]]:
    """L 形样本的货架带：(x0, y0, 长度)，单位毫米。"""
    bands = [
        (LOWER_BAND_X0 * M, (LOWER_BAND_Y0 + i * MODULE / M) * M,
         (LOWER_BAND_X1 - LOWER_BAND_X0) * M)
        for i in range(LOWER_BAND_COUNT)
    ]
    bands += [
        (UPPER_BAND_X0 * M, (UPPER_BAND_Y0 + i * MODULE / M) * M,
         (UPPER_BAND_X1 - UPPER_BAND_X0) * M)
        for i in range(UPPER_BAND_COUNT)
    ]
    return bands


def shape_columns() -> list[tuple[float, float]]:
    """L 形样本的柱心坐标，单位毫米。与 draw 逻辑共用，保证一致。"""
    pitch, size = SHAPE_COLUMN_PITCH, SHAPE_COLUMN_SIZE
    points = []
    for i in range(int(150_000 // pitch) + 2):
        for j in range(int(105_000 // pitch) + 2):
            cx, cy = i * pitch, j * pitch
            if not _shape_inside(cx / M, cy / M):
                continue
            if cx in (0, 150_000) or cy == 0:
                continue
            if cy == SHAPE_SPLIT_Y * M and cx > SHAPE_UPPER_WIDTH * M:
                continue
            points.append((cx, cy))
    return points


def build_l3(path: Path) -> None:
    """L3：空间 + 货架带 + 库区。"""
    _save(_build_shape(with_bands=True), path)


def build_l4(path: Path) -> None:
    """L4：只有空间。"""
    _save(_build_shape(with_bands=False), path)


def _build_shape(with_bands: bool) -> ezdxf.document.Drawing:
    doc = _new_doc()
    msp = doc.modelspace()

    msp.add_lwpolyline(
        [(x * M, y * M) for x, y in SHAPE_OUTLINE],
        close=True,
        dxfattribs={"layer": "OUTLINE"},
    )

    half = SHAPE_COLUMN_SIZE / 2
    for cx, cy in shape_columns():
        _rect(msp, cx - half, cy - half, SHAPE_COLUMN_SIZE, SHAPE_COLUMN_SIZE, "COLUMN")

    if with_bands:
        for x0, y0, length in shape_bands():
            _rect(msp, x0, y0, length, BAND_DEPTH, "BAND")
            _label(msp, BAND_SPEC, x0 + length / 2, y0 + BAND_DEPTH / 2)

    _rect(msp, 0, 0, 150_000, SHAPE_SPLIT_Y * M, "ZONE")
    _label(msp, "A", 144_000, 3_000, 900)
    _rect(msp, 0, SHAPE_SPLIT_Y * M, SHAPE_UPPER_WIDTH * M,
          (105 - SHAPE_SPLIT_Y) * M, "ZONE")
    _label(msp, "B", 90_000, (SHAPE_SPLIT_Y + 2) * M, 900)

    for name, x, y, w, h in SHAPE_FACILITIES:
        _rect(msp, x * M, y * M, w * M, h * M, "FACILITY")
        _label(msp, name, (x + w / 2) * M, (y + h / 2) * M, 700)

    for name, x in SHAPE_DOCKS:
        _rect(msp, x * M, -2_000, 10_000, 2_000, "DOCK")
        _label(msp, name, (x + 5) * M, -1_000, 700)

    return doc


def expected_counts() -> dict[str, int]:
    """L3 样本的期望值，供闭环验证比对。"""
    lower_racks = int((LOWER_BAND_X1 - LOWER_BAND_X0) * M // RACK_LENGTH)
    upper_racks = int((UPPER_BAND_X1 - UPPER_BAND_X0) * M // RACK_LENGTH)
    level_positions = LEVELS * POSITIONS
    total_bands = LOWER_BAND_COUNT + UPPER_BAND_COUNT
    return {
        "bands": total_bands,
        "columns": len(shape_columns()),
        "lower_bands": LOWER_BAND_COUNT,
        "upper_bands": UPPER_BAND_COUNT,
        "racks_per_lower": lower_racks,
        "racks_per_upper": upper_racks,
        "racks": lower_racks * LOWER_BAND_COUNT + upper_racks * UPPER_BAND_COUNT,
        "bins": (lower_racks * LOWER_BAND_COUNT + upper_racks * UPPER_BAND_COUNT)
        * level_positions,
        "lower_bins": lower_racks * LOWER_BAND_COUNT * level_positions,
        "upper_bins": upper_racks * UPPER_BAND_COUNT * level_positions,
    }


def _save(doc, path: Path) -> None:
    """只负责落盘。打印交给调用方，避免测试输出被污染。"""
    path.parent.mkdir(parents=True, exist_ok=True)
    doc.saveas(path)
