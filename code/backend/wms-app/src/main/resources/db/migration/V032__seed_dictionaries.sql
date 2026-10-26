-- 数据字典的种子：客户类型、温区、合规属性、国家、行政区划
--
-- 只种数据，不建结构：表由 V018 建立，本文件不改表、不加列、不加索引。
--
-- 为什么层级用 ext.parent 而不加 parent_id 列：
-- 加列会把字典绑死成树，而多数字典（客户类型、温区、合规属性）是平的。只有行政区划需要
-- 「省属于哪个国家」这一层，把它写进 ext 后任何字典都能带层级，读取侧按 parentValue 过滤，
-- 将来出现第三层也不需要再动表。
--
-- 为什么每一项都写 label 与 label_en：
-- 规格要求界面语言切英文时展示英文名。后端不在接口层回退（两个字段原样返回，由前端按当前语言取），
-- 因此种子数据里两列都必须有值——缺 label_en 的项在英文界面下只能显示中文。
--
-- value 编码约定：
--   客户类型、温区、合规属性用大写短码，列宽分别是 varchar(20)（owner.owner_type）
--     与 varchar(16)（sku.temperature_zone），短码同时约束了这两处不会写出超长值；
--   国家用 ISO 3166-1 alpha-2；
--   行政区划用「国家码 + 州/省码」并让 ext.parent 指向国家码——只写 CA 时无法区分
--     「美国加州」与「加拿大」，前缀把归属写进了 value 本身，也保证 (dict_type, value) 全类型内唯一。
--
-- sort_no 的业务顺序：
--   客户类型、温区、合规属性按业务常用度排列；国家按主要贸易伙伴排列，中国在前。
--   州与省按各自的官方列表顺序（美国按英文名升序、中国按行政区划代码顺序），
--   并且每个国家占一个 1000 的编号区间（US 从 10 起、CN 从 1010 起）：
--   不带 parentValue 查询整个 PROVINCE 时，同一国家的项仍然连续，新增国家取下一个区间即可。
--
-- 幂等：按 dict_type / (dict_type, value) 判重。ux_sys_dict_type 与 ux_sys_dict_item_value
-- 会兜底重复，但仍先判重——重放时靠唯一索引报错来发现「本来就该拒绝」这件事，
-- 失败原因会指向约束名而不是本行。
--
-- 不预置品类（CATEGORY）：品类是独立表（V010），带必填项模板与两级树，不是「类型 + 项」的字典
-- 能表达的。规格里「预置客户类型、品类、温区、合规属性等字典」那句与实际建模不符，
-- 本批不为对齐那句话硬塞一个 CATEGORY 字典——那会造出一份与 category 表并存的第二真源。

-- ① 字典类型
INSERT INTO sys_dict (dict_type, name, name_en, remark)
SELECT v.dict_type, v.name, v.name_en, v.remark
FROM (VALUES
    ('OWNER_TYPE', '客户类型', 'Customer Type', '客户（货主）的类型标签，仅用于筛选、统计与后续报价差异化'),
    ('TEMP_ZONE', '温区', 'Temperature Zone', '商品仓储与运输的温区'),
    ('COMPLIANCE', '合规属性', 'Compliance Attribute', '商品的运输合规属性，引用处只允许选择启用项'),
    ('COUNTRY', '国家', 'Country', '国家，value 用 ISO 3166-1 alpha-2'),
    ('PROVINCE', '行政区划', 'Province / State', '省级行政区划，ext.parent 指向所属国家的 value')
) AS v(dict_type, name, name_en, remark)
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict d WHERE d.dict_type = v.dict_type
);

-- ② 客户类型（规格只要求两类：品牌方、货代同行）
INSERT INTO sys_dict_item (dict_type, label, label_en, value, sort_no)
SELECT v.dict_type, v.label, v.label_en, v.value, v.sort_no
FROM (VALUES
    ('OWNER_TYPE', '品牌方', 'Brand Owner', 'BRAND', 10),
    ('OWNER_TYPE', '货代同行', 'Freight Forwarder', 'FORWARDER', 20)
) AS v(dict_type, label, label_en, value, sort_no)
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_item i WHERE i.dict_type = v.dict_type AND i.value = v.value
);

-- ③ 温区
INSERT INTO sys_dict_item (dict_type, label, label_en, value, sort_no)
SELECT v.dict_type, v.label, v.label_en, v.value, v.sort_no
FROM (VALUES
    ('TEMP_ZONE', '常温', 'Ambient', 'NORMAL', 10),
    ('TEMP_ZONE', '冷藏', 'Chilled', 'CHILLED', 20),
    ('TEMP_ZONE', '冷冻', 'Frozen', 'FROZEN', 30),
    ('TEMP_ZONE', '恒温', 'Constant Temperature', 'CONSTANT', 40)
) AS v(dict_type, label, label_en, value, sort_no)
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_item i WHERE i.dict_type = v.dict_type AND i.value = v.value
);

-- ④ 合规属性
INSERT INTO sys_dict_item (dict_type, label, label_en, value, sort_no)
SELECT v.dict_type, v.label, v.label_en, v.value, v.sort_no
FROM (VALUES
    ('COMPLIANCE', '普货', 'General Cargo', 'GENERAL', 10),
    ('COMPLIANCE', '带电', 'Battery', 'BATTERY', 20),
    ('COMPLIANCE', '液体', 'Liquid', 'LIQUID', 30),
    ('COMPLIANCE', '粉末', 'Powder', 'POWDER', 40),
    ('COMPLIANCE', '磁性', 'Magnetic', 'MAGNETIC', 50),
    ('COMPLIANCE', '危险品', 'Dangerous Goods', 'DANGEROUS', 60)
) AS v(dict_type, label, label_en, value, sort_no)
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_item i WHERE i.dict_type = v.dict_type AND i.value = v.value
);

-- ⑤ 国家：主要贸易伙伴 28 个，value 为 ISO 3166-1 alpha-2，与 owner.country 的 char(2) 口径一致
INSERT INTO sys_dict_item (dict_type, label, label_en, value, sort_no)
SELECT v.dict_type, v.label, v.label_en, v.value, v.sort_no
FROM (VALUES
    ('COUNTRY', '中国', 'China', 'CN', 10),
    ('COUNTRY', '美国', 'United States', 'US', 20),
    ('COUNTRY', '英国', 'United Kingdom', 'GB', 30),
    ('COUNTRY', '德国', 'Germany', 'DE', 40),
    ('COUNTRY', '法国', 'France', 'FR', 50),
    ('COUNTRY', '意大利', 'Italy', 'IT', 60),
    ('COUNTRY', '西班牙', 'Spain', 'ES', 70),
    ('COUNTRY', '荷兰', 'Netherlands', 'NL', 80),
    ('COUNTRY', '比利时', 'Belgium', 'BE', 90),
    ('COUNTRY', '波兰', 'Poland', 'PL', 100),
    ('COUNTRY', '俄罗斯', 'Russia', 'RU', 110),
    ('COUNTRY', '日本', 'Japan', 'JP', 120),
    ('COUNTRY', '韩国', 'South Korea', 'KR', 130),
    ('COUNTRY', '新加坡', 'Singapore', 'SG', 140),
    ('COUNTRY', '马来西亚', 'Malaysia', 'MY', 150),
    ('COUNTRY', '泰国', 'Thailand', 'TH', 160),
    ('COUNTRY', '越南', 'Vietnam', 'VN', 170),
    ('COUNTRY', '印度尼西亚', 'Indonesia', 'ID', 180),
    ('COUNTRY', '印度', 'India', 'IN', 190),
    ('COUNTRY', '澳大利亚', 'Australia', 'AU', 200),
    ('COUNTRY', '新西兰', 'New Zealand', 'NZ', 210),
    ('COUNTRY', '加拿大', 'Canada', 'CA', 220),
    ('COUNTRY', '墨西哥', 'Mexico', 'MX', 230),
    ('COUNTRY', '巴西', 'Brazil', 'BR', 240),
    ('COUNTRY', '阿联酋', 'United Arab Emirates', 'AE', 250),
    ('COUNTRY', '沙特阿拉伯', 'Saudi Arabia', 'SA', 260),
    ('COUNTRY', '土耳其', 'Turkey', 'TR', 270),
    ('COUNTRY', '南非', 'South Africa', 'ZA', 280)
) AS v(dict_type, label, label_en, value, sort_no)
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_item i WHERE i.dict_type = v.dict_type AND i.value = v.value
);

-- ⑥ 行政区划 · 美国 50 州 + 哥伦比亚特区，按英文名升序，ext.parent 指向 US
INSERT INTO sys_dict_item (dict_type, label, label_en, value, sort_no, ext)
SELECT v.dict_type, v.label, v.label_en, v.value, v.sort_no, v.ext::jsonb
FROM (VALUES
    ('PROVINCE', '阿拉巴马州', 'Alabama', 'US-AL', 10, '{"parent": "US"}'),
    ('PROVINCE', '阿拉斯加州', 'Alaska', 'US-AK', 20, '{"parent": "US"}'),
    ('PROVINCE', '亚利桑那州', 'Arizona', 'US-AZ', 30, '{"parent": "US"}'),
    ('PROVINCE', '阿肯色州', 'Arkansas', 'US-AR', 40, '{"parent": "US"}'),
    ('PROVINCE', '加利福尼亚州', 'California', 'US-CA', 50, '{"parent": "US"}'),
    ('PROVINCE', '科罗拉多州', 'Colorado', 'US-CO', 60, '{"parent": "US"}'),
    ('PROVINCE', '康涅狄格州', 'Connecticut', 'US-CT', 70, '{"parent": "US"}'),
    ('PROVINCE', '特拉华州', 'Delaware', 'US-DE', 80, '{"parent": "US"}'),
    ('PROVINCE', '哥伦比亚特区', 'District of Columbia', 'US-DC', 90, '{"parent": "US"}'),
    ('PROVINCE', '佛罗里达州', 'Florida', 'US-FL', 100, '{"parent": "US"}'),
    ('PROVINCE', '佐治亚州', 'Georgia', 'US-GA', 110, '{"parent": "US"}'),
    ('PROVINCE', '夏威夷州', 'Hawaii', 'US-HI', 120, '{"parent": "US"}'),
    ('PROVINCE', '爱达荷州', 'Idaho', 'US-ID', 130, '{"parent": "US"}'),
    ('PROVINCE', '伊利诺伊州', 'Illinois', 'US-IL', 140, '{"parent": "US"}'),
    ('PROVINCE', '印第安纳州', 'Indiana', 'US-IN', 150, '{"parent": "US"}'),
    ('PROVINCE', '艾奥瓦州', 'Iowa', 'US-IA', 160, '{"parent": "US"}'),
    ('PROVINCE', '堪萨斯州', 'Kansas', 'US-KS', 170, '{"parent": "US"}'),
    ('PROVINCE', '肯塔基州', 'Kentucky', 'US-KY', 180, '{"parent": "US"}'),
    ('PROVINCE', '路易斯安那州', 'Louisiana', 'US-LA', 190, '{"parent": "US"}'),
    ('PROVINCE', '缅因州', 'Maine', 'US-ME', 200, '{"parent": "US"}'),
    ('PROVINCE', '马里兰州', 'Maryland', 'US-MD', 210, '{"parent": "US"}'),
    ('PROVINCE', '马萨诸塞州', 'Massachusetts', 'US-MA', 220, '{"parent": "US"}'),
    ('PROVINCE', '密歇根州', 'Michigan', 'US-MI', 230, '{"parent": "US"}'),
    ('PROVINCE', '明尼苏达州', 'Minnesota', 'US-MN', 240, '{"parent": "US"}'),
    ('PROVINCE', '密西西比州', 'Mississippi', 'US-MS', 250, '{"parent": "US"}'),
    ('PROVINCE', '密苏里州', 'Missouri', 'US-MO', 260, '{"parent": "US"}'),
    ('PROVINCE', '蒙大拿州', 'Montana', 'US-MT', 270, '{"parent": "US"}'),
    ('PROVINCE', '内布拉斯加州', 'Nebraska', 'US-NE', 280, '{"parent": "US"}'),
    ('PROVINCE', '内华达州', 'Nevada', 'US-NV', 290, '{"parent": "US"}'),
    ('PROVINCE', '新罕布什尔州', 'New Hampshire', 'US-NH', 300, '{"parent": "US"}'),
    ('PROVINCE', '新泽西州', 'New Jersey', 'US-NJ', 310, '{"parent": "US"}'),
    ('PROVINCE', '新墨西哥州', 'New Mexico', 'US-NM', 320, '{"parent": "US"}'),
    ('PROVINCE', '纽约州', 'New York', 'US-NY', 330, '{"parent": "US"}'),
    ('PROVINCE', '北卡罗来纳州', 'North Carolina', 'US-NC', 340, '{"parent": "US"}'),
    ('PROVINCE', '北达科他州', 'North Dakota', 'US-ND', 350, '{"parent": "US"}'),
    ('PROVINCE', '俄亥俄州', 'Ohio', 'US-OH', 360, '{"parent": "US"}'),
    ('PROVINCE', '俄克拉何马州', 'Oklahoma', 'US-OK', 370, '{"parent": "US"}'),
    ('PROVINCE', '俄勒冈州', 'Oregon', 'US-OR', 380, '{"parent": "US"}'),
    ('PROVINCE', '宾夕法尼亚州', 'Pennsylvania', 'US-PA', 390, '{"parent": "US"}'),
    ('PROVINCE', '罗得岛州', 'Rhode Island', 'US-RI', 400, '{"parent": "US"}'),
    ('PROVINCE', '南卡罗来纳州', 'South Carolina', 'US-SC', 410, '{"parent": "US"}'),
    ('PROVINCE', '南达科他州', 'South Dakota', 'US-SD', 420, '{"parent": "US"}'),
    ('PROVINCE', '田纳西州', 'Tennessee', 'US-TN', 430, '{"parent": "US"}'),
    ('PROVINCE', '得克萨斯州', 'Texas', 'US-TX', 440, '{"parent": "US"}'),
    ('PROVINCE', '犹他州', 'Utah', 'US-UT', 450, '{"parent": "US"}'),
    ('PROVINCE', '佛蒙特州', 'Vermont', 'US-VT', 460, '{"parent": "US"}'),
    ('PROVINCE', '弗吉尼亚州', 'Virginia', 'US-VA', 470, '{"parent": "US"}'),
    ('PROVINCE', '华盛顿州', 'Washington', 'US-WA', 480, '{"parent": "US"}'),
    ('PROVINCE', '西弗吉尼亚州', 'West Virginia', 'US-WV', 490, '{"parent": "US"}'),
    ('PROVINCE', '威斯康星州', 'Wisconsin', 'US-WI', 500, '{"parent": "US"}'),
    ('PROVINCE', '怀俄明州', 'Wyoming', 'US-WY', 510, '{"parent": "US"}')
) AS v(dict_type, label, label_en, value, sort_no, ext)
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_item i WHERE i.dict_type = v.dict_type AND i.value = v.value
);

-- ⑦ 行政区划 · 中国 31 个省级行政区（22 省 + 5 自治区 + 4 直辖市，不含港澳台），
--    按行政区划代码顺序，ext.parent 指向 CN
INSERT INTO sys_dict_item (dict_type, label, label_en, value, sort_no, ext)
SELECT v.dict_type, v.label, v.label_en, v.value, v.sort_no, v.ext::jsonb
FROM (VALUES
    ('PROVINCE', '北京市', 'Beijing', 'CN-BJ', 1010, '{"parent": "CN"}'),
    ('PROVINCE', '天津市', 'Tianjin', 'CN-TJ', 1020, '{"parent": "CN"}'),
    ('PROVINCE', '河北省', 'Hebei', 'CN-HE', 1030, '{"parent": "CN"}'),
    ('PROVINCE', '山西省', 'Shanxi', 'CN-SX', 1040, '{"parent": "CN"}'),
    ('PROVINCE', '内蒙古自治区', 'Inner Mongolia', 'CN-NM', 1050, '{"parent": "CN"}'),
    ('PROVINCE', '辽宁省', 'Liaoning', 'CN-LN', 1060, '{"parent": "CN"}'),
    ('PROVINCE', '吉林省', 'Jilin', 'CN-JL', 1070, '{"parent": "CN"}'),
    ('PROVINCE', '黑龙江省', 'Heilongjiang', 'CN-HL', 1080, '{"parent": "CN"}'),
    ('PROVINCE', '上海市', 'Shanghai', 'CN-SH', 1090, '{"parent": "CN"}'),
    ('PROVINCE', '江苏省', 'Jiangsu', 'CN-JS', 1100, '{"parent": "CN"}'),
    ('PROVINCE', '浙江省', 'Zhejiang', 'CN-ZJ', 1110, '{"parent": "CN"}'),
    ('PROVINCE', '安徽省', 'Anhui', 'CN-AH', 1120, '{"parent": "CN"}'),
    ('PROVINCE', '福建省', 'Fujian', 'CN-FJ', 1130, '{"parent": "CN"}'),
    ('PROVINCE', '江西省', 'Jiangxi', 'CN-JX', 1140, '{"parent": "CN"}'),
    ('PROVINCE', '山东省', 'Shandong', 'CN-SD', 1150, '{"parent": "CN"}'),
    ('PROVINCE', '河南省', 'Henan', 'CN-HA', 1160, '{"parent": "CN"}'),
    ('PROVINCE', '湖北省', 'Hubei', 'CN-HB', 1170, '{"parent": "CN"}'),
    ('PROVINCE', '湖南省', 'Hunan', 'CN-HN', 1180, '{"parent": "CN"}'),
    ('PROVINCE', '广东省', 'Guangdong', 'CN-GD', 1190, '{"parent": "CN"}'),
    ('PROVINCE', '广西壮族自治区', 'Guangxi', 'CN-GX', 1200, '{"parent": "CN"}'),
    ('PROVINCE', '海南省', 'Hainan', 'CN-HI', 1210, '{"parent": "CN"}'),
    ('PROVINCE', '重庆市', 'Chongqing', 'CN-CQ', 1220, '{"parent": "CN"}'),
    ('PROVINCE', '四川省', 'Sichuan', 'CN-SC', 1230, '{"parent": "CN"}'),
    ('PROVINCE', '贵州省', 'Guizhou', 'CN-GZ', 1240, '{"parent": "CN"}'),
    ('PROVINCE', '云南省', 'Yunnan', 'CN-YN', 1250, '{"parent": "CN"}'),
    ('PROVINCE', '西藏自治区', 'Tibet', 'CN-XZ', 1260, '{"parent": "CN"}'),
    ('PROVINCE', '陕西省', 'Shaanxi', 'CN-SN', 1270, '{"parent": "CN"}'),
    ('PROVINCE', '甘肃省', 'Gansu', 'CN-GS', 1280, '{"parent": "CN"}'),
    ('PROVINCE', '青海省', 'Qinghai', 'CN-QH', 1290, '{"parent": "CN"}'),
    ('PROVINCE', '宁夏回族自治区', 'Ningxia', 'CN-NX', 1300, '{"parent": "CN"}'),
    ('PROVINCE', '新疆维吾尔自治区', 'Xinjiang', 'CN-XJ', 1310, '{"parent": "CN"}')
) AS v(dict_type, label, label_en, value, sort_no, ext)
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_item i WHERE i.dict_type = v.dict_type AND i.value = v.value
);
