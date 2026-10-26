-- 统一编码规则的种子
--
-- 只预置货主编码：`owner.code` 是 NOT NULL 且无默认值，客户建档必须由规则取号，
-- 因此在货主档案（implement-owner 3.1）落地前，这条规则必须是已存在的行。
--
-- 形如 OWNER0001：prefix 与流水位数分开配，拼装由取号引擎完成，不在库里拼。
-- current_value 从 0 起：取号语句先把 current_value 加 step 再返回，
-- 所以第一次取到的是 0001，不是 0000。这与列注释「当前已分配到的流水号」一致。
--
-- SKUID 规则不在这里预置：它的位数与流水粒度由 product-center 规格决定
-- （货主前缀 + 按货主独立递增），不是本表「单条规则单条流水」能表达的固定前缀，
-- 归属 implement-sku 处理。
--
-- 幂等：ux_sys_code_rule_code 会兜底重复，但仍先判重——迁移重放时靠唯一索引报错来
-- 发现「本来就不该插」这件事，失败原因会指向约束名而不是本文件。

INSERT INTO sys_code_rule (rule_code, name, prefix, seq_length, current_value, step, remark)
SELECT 'OWNER', '货主编码', 'OWNER', 4, 0, 1, '客户建档自动取号，形如 OWNER0001'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_code_rule WHERE rule_code = 'OWNER'
);
