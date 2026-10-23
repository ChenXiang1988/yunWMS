-- 品类（最多两级）
-- 品类由 3PL 统一维护，承载该品类的必填项模板，所有货主共用。

CREATE TABLE category (
    id               bigint       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    parent_id        bigint       REFERENCES category (id),
    level            smallint     NOT NULL,

    code             varchar(32)  NOT NULL,
    name             varchar(64)  NOT NULL,
    name_en          varchar(64),

    -- 该品类要求的附加必填项，元素为字段标识，如 battery_type、shelf_life_days
    required_fields  text[]       NOT NULL DEFAULT '{}',

    sort_no          int          NOT NULL DEFAULT 0,
    status           varchar(10)  NOT NULL DEFAULT 'ACTIVE',

    created_at       timestamptz  NOT NULL DEFAULT now(),
    created_by       bigint,
    updated_at       timestamptz  NOT NULL DEFAULT now(),
    updated_by       bigint,
    deleted_at       timestamptz,

    -- 一级没有父、二级必须有父，防止出现三级
    CONSTRAINT ck_category_level CHECK (
        (level = 1 AND parent_id IS NULL)
        OR (level = 2 AND parent_id IS NOT NULL)
    ),
    CONSTRAINT ck_category_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);

CREATE UNIQUE INDEX ux_category_code ON category (code);

CREATE INDEX ix_category_parent ON category (parent_id, sort_no);

-- CHECK 只能保证 level 与 parent 的一致性，挡不住「二级挂在二级下」这类逻辑三级。
-- 用触发器把父节点必须为一级这条规则落到库上。
CREATE FUNCTION category_assert_parent_is_root() RETURNS trigger AS $$
BEGIN
    IF NEW.parent_id IS NOT NULL
       AND (SELECT level FROM category WHERE id = NEW.parent_id) <> 1 THEN
        RAISE EXCEPTION '品类最多两级，父节点必须是一级品类';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_category_parent
    BEFORE INSERT OR UPDATE OF parent_id ON category
    FOR EACH ROW EXECUTE FUNCTION category_assert_parent_is_root();

COMMENT ON TABLE  category IS '商品品类，最多两级，由 3PL 统一维护';
COMMENT ON COLUMN category.level IS '1 一级品类 / 2 二级子类，不允许三级';
COMMENT ON COLUMN category.required_fields IS '该品类追加的必填字段标识数组，选择该品类后追加到提交校验';
