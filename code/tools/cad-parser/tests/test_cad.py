"""单元测试。

与 `wmscad verify` 的区别：
  verify  是给人看的闭环报告，逐项列出实际值与期望值
  本文件  是给 CI 的断言，失败即红

两者共用 wmscad.checks，避免维护两套期望值。
"""

from __future__ import annotations

import unittest
from pathlib import Path

from wmscad import checks, fixtures
from wmscad.geom import Rect, intersect, point_in_polygon, polygon_area, subtract
from wmscad.layout import auto_layout
from wmscad.parser import parse, parse_spec

SQUARE = [(0, 0), (10, 0), (10, 10), (0, 10)]


class RectTest(unittest.TestCase):
    def test_edges(self):
        rect = Rect(10, 20, 30, 40)
        self.assertEqual((rect.x2, rect.y2), (40, 60))
        self.assertEqual((rect.cx, rect.cy), (25, 40))
        self.assertEqual((rect.long_side, rect.short_side), (40, 30))

    def test_contains_with_margin(self):
        rect = Rect(0, 0, 10, 10)
        self.assertTrue(rect.contains(5, 5))
        self.assertFalse(rect.contains(11, 5))
        self.assertTrue(rect.contains(11, 5, margin=2))

    def test_overlaps_touching_is_not_overlap(self):
        self.assertTrue(Rect(0, 0, 10, 10).overlaps(Rect(5, 5, 10, 10)))
        self.assertFalse(Rect(0, 0, 10, 10).overlaps(Rect(10, 0, 10, 10)))


class GeometryTest(unittest.TestCase):
    def test_polygon_area(self):
        self.assertEqual(polygon_area(SQUARE), 100)

    def test_point_in_polygon(self):
        self.assertTrue(point_in_polygon(5, 5, SQUARE))
        self.assertFalse(point_in_polygon(15, 5, SQUARE))

    def test_subtract(self):
        self.assertEqual(subtract([(0, 10)], [(3, 6)]), [(0, 3), (6, 10)])
        self.assertEqual(subtract([(0, 10)], [(0, 10)]), [])

    def test_intersect(self):
        self.assertEqual(intersect([(0, 10)], [(5, 15)]), [(5, 10)])
        self.assertEqual(intersect([(0, 10)], [(20, 30)]), [])


class SpecTest(unittest.TestCase):
    def test_parse_spec(self):
        self.assertEqual(parse_spec("4L2P"), (4, 2))
        self.assertEqual(parse_spec(" 4l2p "), (4, 2))
        self.assertIsNone(parse_spec("A"))
        self.assertIsNone(parse_spec("4L"))


class RoundTripTest(unittest.TestCase):
    """生成 → 解析 → 比对，验证图纸表达与数据模型一致。"""

    def test_checks_all_pass(self):
        failures = [c for c in checks.run_all() if not c.ok]
        self.assertEqual(failures, [], "\n".join(f"{c.name}: {c.detail}" for c in failures))


class LayoutTest(unittest.TestCase):
    def test_bands_stay_inside_and_avoid_obstacles(self):
        outline = [(0, 0), (60_000, 0), (60_000, 40_000), (0, 40_000)]
        obstacle = Rect(20_000, 10_000, 5_000, 5_000)
        bands = auto_layout(
            outline,
            [obstacle],
            band_depth=2_200,
            aisle_width=2_000,
            rack_length=2_700,
            levels=4,
            positions=2,
        )
        self.assertTrue(bands)
        for band in bands:
            self.assertFalse(band.rect.overlaps(obstacle))
            for x, y in ((band.rect.x, band.rect.y), (band.rect.x2, band.rect.y2)):
                self.assertTrue(point_in_polygon(x, y, outline))

    def test_empty_outline_returns_empty(self):
        self.assertEqual(auto_layout([], []), [])


class ParseFailureTest(unittest.TestCase):
    def test_missing_file_raises(self):
        from wmscad.reader import DrawingError

        with self.assertRaises(DrawingError):
            parse(Path("/nonexistent/does-not-exist.dxf"))


class FixtureTest(unittest.TestCase):
    def test_expected_counts_are_stable(self):
        expected = fixtures.expected_counts()
        self.assertEqual(expected["bands"], 24)
        self.assertEqual(expected["racks"], 894)
        self.assertEqual(expected["bins"], 7152)
        self.assertEqual(expected["columns"], 174)


if __name__ == "__main__":
    unittest.main(verbosity=2)
