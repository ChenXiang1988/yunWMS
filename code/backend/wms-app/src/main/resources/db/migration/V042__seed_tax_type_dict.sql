-- TAX_ID_TYPE 字典：货主税号类型，以及「维护税号」权限点
--
-- 只种数据，不建结构：字典表由 V018 建立，权限点表由 V004 建立，本文件不改表、不加列、不加索引。
--
-- 消费方是 owner_tax_registration.tax_type（V009，varchar(20) NOT NULL，列注释写明「取自数据字典」）。
-- 本字典最长的取值 US_STATE_SALES 为 14 字符，短码口径保证列宽永远够用。
--
-- 为什么类型做成字典而不是枚举：规格要求「税号类型取自字典并与国家匹配」，而匹配关系会随贸易
-- 伙伴变化——今天只有中美的专用类型，明天可能要加欧盟的 EORI、印度的 GSTIN。做成字典后，新增一个
-- 国家的税号类型是插一行数据并写这一项的 ext.countries，不需要改 Java 枚举、表约束与前端下拉。
--
-- 为什么「适用国家」写在 ext.countries：与 V032 用 ext.parent 承载行政区划层级是同一种做法——
-- 多数字典是平的，只有个别项需要额外约束，加列会把字典绑死成「每个字典都有国家维度」。
-- 键的语义（判定由 wms-app 的 DictLookupAdapter 实现）：
--   缺该键、值为 null、或为空数组 → 不限国家；
--   数组里有该国家 → 适用；数组里没有 → 不适用。
-- VAT 用空对象 {}，即「不限国家」：VAT 跨欧盟多国且清单会持续变，写死一份国家清单等于给自己
-- 造一个必然过期的第二真源，将来每加一个成员国都要改这行数据。
--
-- 编码为什么带国家前缀（CN_USCC / US_EIN / US_STATE_SALES）：
-- V009 的列注释举例的是「EIN、STATE_SALES、VAT、GST」，但那句是列用途说明，不是编码定义——
-- 本字典才是唯一真源。加上国家前缀是因为 EIN、州销售税号都是美国的制度，不带前缀的 EIN 将来
-- 若出现同名他国税号就只能改名，而 tax_type 已经落库，改名要写迁移搬数据。
--
-- 为什么两项美国类型并列而不是合并成 US_TAX：EIN 是联邦雇主识别号，州销售税号是按州登记的
-- （规格的「多州税号并存」场景要求同一货主在加州与德州各存一条州销售税号），两者的唯一性口径
-- 与业务含义都不同，合并后 ux_owner_tax_unique 的 (owner_id, country, province, tax_type)
-- 就无法区分「同州的联邦税号」与「同州的州税号」。
--
-- sort_no 的顺序：中国的统一社会信用代码在最前（主要业务在中国），其后美国两种，VAT 最后——
-- 它是跨国的兜底项，排在使用国专用类型之后，避免用户在中国、美国的场景里先看到通用项。
-- 步长 10 留给将来插项。
--
-- status 不显式写，走列默认 ACTIVE（同 V032、V039）：字典项要能被选择，停用是后续管理动作。
--
-- 每一项都写 label 与 label_en：与 V032 同口径——后端不在接口层回退，两个字段原样返回、
-- 由前端按当前语言取，缺 label_en 的项在英文界面下只能显示中文。
--
-- 幂等：按 dict_type / (dict_type, value) 判重。ux_sys_dict_type 与 ux_sys_dict_item_value
-- 会兜底重复，但仍先判重——重放时靠唯一索引报错来发现「本来就该拒绝」这件事，
-- 失败原因会指向约束名而不是本行。

-- ① 字典类型
INSERT INTO sys_dict (dict_type, name, name_en, remark)
SELECT v.dict_type, v.name, v.name_en, v.remark
FROM (VALUES
    ('TAX_ID_TYPE', '税号类型', 'Tax ID Type', '货主税号类型，ext.countries 列出适用国家，为空表示不限国家')
) AS v(dict_type, name, name_en, remark)
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict d WHERE d.dict_type = v.dict_type
);

-- ② 字典项（四项：中国的统一社会信用代码、美国的 EIN 与州销售税号、跨国的 VAT）
INSERT INTO sys_dict_item (dict_type, label, label_en, value, sort_no, ext)
SELECT v.dict_type, v.label, v.label_en, v.value, v.sort_no, v.ext::jsonb
FROM (VALUES
    ('TAX_ID_TYPE', '统一社会信用代码', 'Unified Social Credit Code',       'CN_USCC',        10, '{"countries": ["CN"]}'),
    ('TAX_ID_TYPE', '雇主识别号',       'Employer Identification Number',    'US_EIN',         20, '{"countries": ["US"]}'),
    ('TAX_ID_TYPE', '州销售税号',       'State Sales Tax ID',                'US_STATE_SALES', 30, '{"countries": ["US"]}'),
    ('TAX_ID_TYPE', '增值税号',         'VAT Number',                        'VAT',            40, '{}')
) AS v(dict_type, label, label_en, value, sort_no, ext)
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_item i WHERE i.dict_type = v.dict_type AND i.value = v.value
);

-- ③ 权限点「维护税号」：挂在 V033 建立的 C 节点「货主档案」下，与其余 owner 操作点同级
--
-- 为什么不复用 system:owner:update：税号是货主的独立子表（owner_tax_registration），登记的是
-- 「在哪几个国家、哪几个州纳什么税」，与货主主体的编码、结算主体、注册地址不在同一份表单里提交。
-- 合并成一个点之后，任何被授予「改个电话」的角色都能顺手改掉税务登记；而税号直接决定开票与
-- 清关口径，变更责任人通常与维护档案的不是同一岗位，拆开才能分别授予与收回。
-- 权限矩阵对该能力只列了一个点（维护税号），不拆 CRUD，因此这里只插 1 个 F 节点。
--
-- 权限点编码：权限矩阵写的是 owner:tax:edit，本仓库自 V033 起的实现统一用 system:owner: 前缀，
-- 这里沿用实现前缀 system:owner:tax:edit——鉴权按库里的编码逐字比对，前后端必须同串；
-- 存量权限点编码本次不动（已生效的迁移禁止修改）。与 V040 处理联系人权限点的裁决一致。
--
-- 只插 F 节点、不插 sys_role_menu：内置超级管理员的权限在鉴权时按「持有 builtin 角色」动态展开
-- 为 sys_menu 里的全部权限点，一旦这里补授权行，新增权限点时就会漏补且不报错。理由与 V033 第 ⑥ 段一致。
--
-- 父节点用子查询取，不写死 id：id 是 GENERATED ALWAYS AS IDENTITY，写死要求本文件按某个隐含
-- 顺序执行，而迁移顺序只由版本号保证。C 节点由 V033 建立，先后由版本号保证。
--
-- sort_no 取同级 F 节点的当前最大值 + 10，不写死固定值：本文件排在 V041 之后只是当前的事实。
-- 多条分支并行时，另一条分支完全可能也给同一个父节点加 F 节点并从同一个号开始写；
-- (parent_id, sort_no) 没有唯一约束，两行同号会静默共存，界面上两个按钮的先后变成偶然。
-- 动态取最大值后，谁后执行谁拿下一个号，合并顺序不同也不会撞车。
--
-- 幂等：F 层由 ux_sys_menu_perms 兜底，但同样先判重，避免重复执行时靠异常发现「本来就该拒绝」这件事。

INSERT INTO sys_menu (parent_id, menu_type, name, perms, sort_no)
SELECT p.id,
       'F',
       '维护税号',
       'system:owner:tax:edit',
       COALESCE(
           (SELECT max(s.sort_no) FROM sys_menu s
            WHERE s.parent_id = p.id AND s.menu_type = 'F'),
           0) + 10
FROM sys_menu p
WHERE p.menu_type = 'C'
  AND p.path = '/master/owners'
  AND NOT EXISTS (
      SELECT 1 FROM sys_menu WHERE perms = 'system:owner:tax:edit'
  );
