-- 收紧 SKU 前缀的库级约束，与领域规则对齐
--
-- 原约束（V001）：CHECK (sku_prefix ~ '^[A-Z0-9]{4}$')
-- 领域规则（SkuPrefix）：^[A-HJ-NP-Z2-9]{4}$
--
-- 差在四个字符：0、O、1、I。规格要求排除它们，理由是货位标签与拣货单上手写时
-- 最容易认错。原约束只挡格式、不挡易混字符，于是存在一条窗口：
-- 绕过接口直接写库，能存进「库里合法、读出来非法」的货主，而读路径走聚合构造器
-- 会当场失败，表现为列表和详情报 50000。
--
-- 这个缺口不是推理出来的：3.1b2 的集成测试读到两处历史夹具（前缀 UMT1 / UAD1）时
-- 真的 50000 了。
--
-- 数据库是最后一道防线，它必须至少和领域规则一样严。比领域宽是危险的，比领域窄
-- 只是冗余。

ALTER TABLE owner DROP CONSTRAINT ck_owner_sku_prefix;

ALTER TABLE owner ADD CONSTRAINT ck_owner_sku_prefix
    CHECK (sku_prefix ~ '^[A-HJ-NP-Z2-9]{4}$');

COMMENT ON CONSTRAINT ck_owner_sku_prefix ON owner IS
    'SKU 前缀：4 位大写字母或数字，排除易混字符 0/O/1/I。与领域模型 SkuPrefix 的规则必须一致';
