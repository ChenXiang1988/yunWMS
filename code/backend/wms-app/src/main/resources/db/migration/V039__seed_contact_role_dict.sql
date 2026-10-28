-- CONTACT_ROLE 字典：货主联系人的角色
--
-- 只种数据，不建结构：表由 V018 建立，本文件不改表、不加列、不加索引。
--
-- 为什么角色做成字典而不是枚举列：customer-management 规格的「角色扩展」场景要求新增角色时
-- 「通过字典增加角色项即可，联系人表结构不变」，同一需求也把「至少包含业务对接人、财务联系人、
-- 物流联系人、现场联系人与退货联系人」写成数据要求而非代码要求。做成字典后，新增角色是插一行数据，
-- 不需要发版；做成枚举或 CHECK 则每次扩角色都要改表约束，还要同步 Java 枚举与前端下拉。
--
-- 为什么不并入 V032：V032 已生效，已生效的迁移禁止修改（docs/coding-java.md「数据库迁移」）；
-- 且 V032 那批是客户类型、温区、合规属性、国家、行政区划，与联系人角色不同批次发布。
--
-- value 编码用大写短码：owner_contact.role_type 是 varchar(20)，本字典最长的 LOGISTICS 为 9 字符，
-- 短码口径保证列宽永远够用；同时与 V032 的客户类型、温区、合规属性一致，读取侧不必区分大小写风格。
--
-- 为什么每一项都写 label 与 label_en：与 V032 同口径——后端不在接口层回退，两个字段原样返回、
-- 由前端按当前语言取，缺 label_en 的项在英文界面下只能显示中文。
--
-- status 不显式写，走列默认 ACTIVE（同 V032）：字典项要能被选择，停用是后续管理动作而不是种子状态。
--
-- sort_no 的顺序：业务对接人在首位，因为提交货主档案时它必须有（规格的「缺少业务对接人」场景）；
-- 其后按财务、物流、现场、退货，与任务给定的顺序一致，步长 10 留给将来插项。
--
-- 幂等：按 dict_type / (dict_type, value) 判重。ux_sys_dict_type 与 ux_sys_dict_item_value 会兜底重复，
-- 但仍先判重——重放时靠唯一索引报错来发现「本来就该拒绝」这件事，失败原因会指向约束名而不是本行。

-- ① 字典类型
INSERT INTO sys_dict (dict_type, name, name_en, remark)
SELECT v.dict_type, v.name, v.name_en, v.remark
FROM (VALUES
    ('CONTACT_ROLE', '联系人角色', 'Contact Role', '货主联系人的角色，owner_contact.role_type 取自本字典')
) AS v(dict_type, name, name_en, remark)
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict d WHERE d.dict_type = v.dict_type
);

-- ② 字典项（规格要求至少这五项）
INSERT INTO sys_dict_item (dict_type, label, label_en, value, sort_no)
SELECT v.dict_type, v.label, v.label_en, v.value, v.sort_no
FROM (VALUES
    ('CONTACT_ROLE', '业务对接人', 'Business Contact',  'BUSINESS',  10),
    ('CONTACT_ROLE', '财务联系人', 'Finance Contact',   'FINANCE',   20),
    ('CONTACT_ROLE', '物流联系人', 'Logistics Contact', 'LOGISTICS', 30),
    ('CONTACT_ROLE', '现场联系人', 'On-site Contact',   'ONSITE',    40),
    ('CONTACT_ROLE', '退货联系人', 'Returns Contact',   'RETURN',    50)
) AS v(dict_type, label, label_en, value, sort_no)
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_item i WHERE i.dict_type = v.dict_type AND i.value = v.value
);
