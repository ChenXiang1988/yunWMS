# 仓库平面图解析工具

按 `docs/standards/cad-drawing-spec.md` 解析客户成功团队交付的仓库平面图，
把「货架带」展开成「货架 + 库位」，或从纯空间图纸自动排布。

**这不是产品代码**，是算法验证与对拍基准。产品侧用 Java 实现，必须与本工具输出一致。

## 两条路径

| 路径 | 团队画什么 | 系统做什么 |
| --- | --- | --- |
| **L3** | 外轮廓、柱子、设施、**货架带** | 展开带 → 货架 → 库位 |
| **L4** | 只画外轮廓、柱子、设施 | **先排布**再展开 |

货架方案已定的用 L3，还只是空仓的用 L4。

## 安装

```bash
pip install -r requirements.txt
```

## 命令行

```bash
python -m wmscad parse  <图纸.dxf>                    # 解析并打印结构
python -m wmscad layout <图纸.dxf>                    # 自动排布货架带
python -m wmscad render <图纸.dxf> --out a.png \
       [--mode schematic|native] [--auto] [--no-bands]
python -m wmscad gen --kind flat|floor|l3|l4 --out b.dxf
python -m wmscad verify                               # 闭环验证
```

`render --mode native` 按 CAD 原始样式渲染，用来验证「提取出来的图与原图是否一致」；
`--mode schematic` 是带标注的示意图，用来核对结构与数量。

## 示例

```bash
# 生成两份交付样例
python -m wmscad gen --kind flat  --out fixtures/input_flat.dxf
python -m wmscad gen --kind floor --out fixtures/input_floor.dxf

# 渲染（--no-bands 表示只画空间，即团队实际交付的样子）
python -m wmscad render fixtures/input_flat.dxf --no-bands --out fixtures/input_flat.png

# 从纯空间图纸自动排布
python -m wmscad layout fixtures/input_flat.dxf
```

## 代码结构

```
wmscad/
├── geom.py       Rect、多边形面积、扫描线区间、区间加减
├── model.py      Band / Zone / Facility / ParseResult
├── reader.py     DXF 读取与块递归展开
├── parser.py     图纸 → 空间事实
├── layout.py     L4 自动排布
├── fixtures.py   测试与演示图纸生成
├── render.py     示意图与原样图两种渲染
├── checks.py     闭环验证项（CLI 与测试共用）
└── cli.py        命令行入口
tests/test_cad.py 单元测试
```

分层原则：`geom` 不认识图纸，`parser` 不认识绘图，`render` 不认识业务。
新增一个图层只需要改 `parser.py`，新增一种渲染只需要改 `render.py`。

## L4 排布算法

扫描线推进，不依赖几何库：

```
沿 Y 按「带深 + 通道宽」的模数逐条推进
每条带取带顶与带底两处横截面的交集   → 保证不跨形状边界
再用障碍物做区间减法                 → 保证不压柱子与设施
最后收缩墙边余量，不足 3 个货架的碎段丢弃
```

关键行为：**柱子落在通道里时不阻断货架带，落在带上时才切段。**
柱距通常是「带深 + 通道」的整数倍，所以真实货架排布里柱子天然落在通道中。

## 验证

```bash
python -m wmscad verify              # 24 项，给人看的报告
python -m unittest discover -s tests # 13 项，给 CI 的断言
```

两者共用 `checks.py` 的期望值，不会出现两套标准。

当前结果：

| 图纸 | 结果 |
| --- | --- |
| L3 合规样本 | 24 条带、894 货架、**7152 库位** |
| L4 空间图 + 自动排布 | 25 条带、1072 货架、**8576 库位** |
| `1007晧物仓.dxf`（真实图，未按规范） | 柱子读到一批、货架带 **0 条** |

L4 比 L3 多出库位不是算法更优，而是 L3 的带由人工绘制（上腿只画到 50 m），L4 把可用空间用满了。

## 已知边界

- 只认 `OUTLINE` `COLUMN` `FACILITY` `DOCK` `AISLE` `ZONE` `BAND` `TEXT` 八个图层，其余忽略
- 柱子打断货架带时，L3 要求团队自行画成两条短带；L4 由算法切段
- 未覆盖：双深位货架、异形库区的精细排布、非正交布局

## 图纸约定

详见 `docs/standards/cad-drawing-spec.md`。核心四条：

1. 单位 mm，模型空间 1:1
2. 一个文件一套平面（楼层仓可多套并排，靠层号文字区分）
3. 货架带画成闭合矩形，内附规格文字（如 `4L2P`）
4. 柱子用实际尺寸绘制，禁止用插件自定义对象
