-- 补 PROVINCE 字典的港澳台三条
--
-- 为什么现在补：V032 只种了中国内地的 31 个省级单位（22 省 + 5 自治区 + 4 直辖市），缺香港、澳门、台湾。
-- 三地同属中国，缺了它们「注册国家选中国」就选不到对应的州省，货主建档会卡在州省下拉框上。
-- 已生效的迁移禁止修改（docs/coding-java.md「数据库迁移」），因此另起一份，不改 V032。
--
-- 为什么写 postalCodeRequired=false：港澳台没有邮政编码，而规格要求注册地址包含邮政编码。
-- 3.1 已裁决为「按地区豁免」而不是「一律必填」，判定读的是字典项的 ext.postalCodeRequired，
-- 缺失该键时为 true（默认必填）——只有明确无邮编的地区才写 false，内地各省因此不写这个键。
--
-- sort_no 接在既有 CN 之后：V032 给中国占了 1010 起的号段、步长 10，当前最大 1310（新疆）。
-- 这三条用 1320/1330/1340，既接在最大号之后，又保持同一节奏，且仍在中国的号段内——
-- 不带 parentValue 查全量 PROVINCE 时，它们紧跟内地各省，不会跳到下一个国家。
--
-- 幂等：与 V032 同口径，按 (dict_type, value) 判重。ux_sys_dict_item_value 会兜底重复，
-- 但仍先判重：重放时靠唯一索引报错来发现「本来就该拒绝」，失败原因会指向约束名而不是本行。
--
-- ext 显式写成 ::jsonb 字面量：VALUES 里的文本列默认推断为 text，写入 jsonb 列依赖赋值转换，
-- 显式标注后列类型一眼可辨，也不会因为将来有人改动这一行而退化成隐式转换。

INSERT INTO sys_dict_item (dict_type, label, label_en, value, sort_no, ext)
SELECT v.dict_type, v.label, v.label_en, v.value, v.sort_no, v.ext
FROM (VALUES
    ('PROVINCE', '香港特别行政区', 'Hong Kong SAR', 'CN-HK', 1320, '{"parent":"CN","postalCodeRequired":false}'::jsonb),
    ('PROVINCE', '澳门特别行政区', 'Macao SAR', 'CN-MO', 1330, '{"parent":"CN","postalCodeRequired":false}'::jsonb),
    ('PROVINCE', '台湾省', 'Taiwan Province', 'CN-TW', 1340, '{"parent":"CN","postalCodeRequired":false}'::jsonb)
) AS v(dict_type, label, label_en, value, sort_no, ext)
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_item i WHERE i.dict_type = v.dict_type AND i.value = v.value
);
