#!/usr/bin/env python3
"""DDL 约束验证。

在一个干净的 PostgreSQL 上依次执行本目录的 SQL，然后逐条验证据约束是否生效。
验证的是「数据库能不能挡住错误数据」，不是应用层的校验。

用法：
    initdb -D /tmp/wmsddl -U postgres --auth=trust
    pg_ctl -D /tmp/wmsddl -o "-p 55432 -k /tmp" start
    # 迁移脚本在后端工程里，Flyway 也用同一份
    for f in ../wms-app/src/main/resources/db/migration/V*.sql; do
        psql -h /tmp -p 55432 -U postgres -d postgres -q -v ON_ERROR_STOP=1 -f "$f"
    done
    python3 verify_constraints.py

脚本假设库是空的，用到固定自增 ID（1、2 号货主）。
"""

from __future__ import annotations

import subprocess
import sys

PSQL = ["psql", "-h", "/tmp", "-p", "55432", "-U", "postgres", "-d", "postgres", "-q", "-c"]

OWNER = "INSERT INTO owner(code,name,sku_prefix,owner_type,settlement_entity,country,city,address_line)"
SKU = "INSERT INTO sku(sku_id,owner_id,owner_sku_code,name,uom) VALUES"

SETUP = [
    f"{OWNER} VALUES('OWNER0001','宝洁','AMZ2','BRAND','宝洁中国','CN','广州','天河路1号')",
    f"{OWNER} VALUES('OWNER0002','联合利华','UNL2','BRAND','联合利华','CN','上海','南京路1号')",
    "INSERT INTO category(parent_id,level,code,name) VALUES(NULL,1,'BEAUTY','美妆')",
    "INSERT INTO category(parent_id,level,code,name) VALUES(1,2,'SKINCARE','护肤')",
    "INSERT INTO brand(owner_id,name) VALUES(1,'Nike')",
]

CASES: list[tuple[str, str, bool]] = [
    # ---- 货主 ----
    ("货主编码重复", f"{OWNER} VALUES"
     "('OWNER0001','联合利华','UNL9','BRAND','x','CN','上海','y')", False),
    ("SKU 前缀小写", f"{OWNER} VALUES"
     "('OWNER0003','测试','amz2','BRAND','x','CN','深圳','z')", False),
    ("SKU 前缀重复", f"{OWNER} VALUES"
     "('OWNER0004','测试','AMZ2','BRAND','x','CN','深圳','z')", False),

    # ---- 用户 ----
    ("员工无邮箱被拒",
     "INSERT INTO sys_user(login_name,display_name,subject_type,password_hash) "
     "VALUES('zhangsan','张三','STAFF','x')", False),
    ("员工有邮箱可建",
     "INSERT INTO sys_user(login_name,display_name,subject_type,password_hash,email) "
     "VALUES('zhangsan','张三','STAFF','x','z@a.com')", True),
    ("货主用户邮箱可空",
     "INSERT INTO sys_user(login_name,display_name,subject_type,owner_id,password_hash,email) "
     "VALUES('OWNER0001','宝洁主账号','OWNER',1,'x',NULL)", True),
    ("员工不允许绑货主",
     "INSERT INTO sys_user(login_name,display_name,subject_type,owner_id,password_hash,email) "
     "VALUES('x1','x','STAFF',1,'x','a@b.com')", False),
    ("登录名大小写不敏感唯一",
     "INSERT INTO sys_user(login_name,display_name,subject_type,password_hash,email) "
     "VALUES('ZhangSan','张三2','STAFF','x','z2@a.com')", False),

    # ---- 负责客服一主一副 ----
    ("第一个主负责人",
     "INSERT INTO owner_assignee(owner_id,user_id,relation_type) "
     "SELECT 1,id,'PRIMARY' FROM sys_user WHERE login_name='zhangsan'", True),
    ("第二个主负责人被拒",
     "INSERT INTO owner_assignee(owner_id,user_id,relation_type) "
     "SELECT 1,id,'PRIMARY' FROM sys_user WHERE login_name='zhangsan'", False),
    ("副负责人可建",
     "INSERT INTO owner_assignee(owner_id,user_id,relation_type) "
     "SELECT 1,id,'SECONDARY' FROM sys_user WHERE login_name='zhangsan'", True),

    # ---- 主要联系人唯一 ----
    ("第一个主要联系人",
     "INSERT INTO owner_contact(owner_id,name,is_primary) VALUES(1,'李四',true)", True),
    ("第二个主要联系人或被拒",
     "INSERT INTO owner_contact(owner_id,name,is_primary) VALUES(1,'王五',true)", False),
    ("非主要联系人不受限",
     "INSERT INTO owner_contact(owner_id,name,is_primary) VALUES(1,'王五',false)", True),

    # ---- 税号 ----
    ("税号空省份第一条",
     "INSERT INTO owner_tax_registration(owner_id,country,tax_type,tax_number) "
     "VALUES(1,'US','EIN','12-345')", True),
    ("税号空省份重复被拒",
     "INSERT INTO owner_tax_registration(owner_id,country,tax_type,tax_number) "
     "VALUES(1,'US','EIN','99-999')", False),
    ("同国第一个默认税号",
     "INSERT INTO owner_tax_registration(owner_id,country,province,tax_type,tax_number,is_default) "
     "VALUES(1,'US','CA','STATE_SALES','CA-1',true)", True),
    ("同国第二个默认被拒",
     "INSERT INTO owner_tax_registration(owner_id,country,province,tax_type,tax_number,is_default) "
     "VALUES(1,'US','NY','STATE_SALES','NY-1',true)", False),

    # ---- 品类 ----
    ("二级品类可建", "SELECT 1", True),
    ("二级挂在二级下被拒",
     "INSERT INTO category(parent_id,level,code,name) VALUES(2,2,'X','三级')", False),
    ("level=3 被拒",
     "INSERT INTO category(parent_id,level,code,name) VALUES(1,3,'Y','y')", False),
    ("一级品类带父被拒",
     "INSERT INTO category(parent_id,level,code,name) VALUES(1,1,'Z','z')", False),

    # ---- 品牌 ----
    ("同货主品牌重名被拒", "INSERT INTO brand(owner_id,name) VALUES(1,'Nike')", False),
    ("跨货主同名品牌可建", "INSERT INTO brand(owner_id,name) VALUES(2,'Nike')", True),

    # ---- 商品 ----
    ("正常建 SKU", f"{SKU}('AMZ2000001',1,'P001','洗发水','件')", True),
    ("SKUID 重复", f"{SKU}('AMZ2000001',1,'P002','沐浴露','件')", False),
    ("SKUID 位数不对", f"{SKU}('AMZ200001',1,'P003','香皂','件')", False),
    ("SKUID 含小写", f"{SKU}('amz2000002',1,'P004','牙膏','件')", False),
    ("货主商品编码重复", f"{SKU}('AMZ2000002',1,'P001','牙刷','件')", False),
    ("另一货主同编码可建", f"{SKU}('UNL2000001',2,'P001','洗发水','件')", True),
    ("图片 6 张被拒",
     "INSERT INTO sku(sku_id,owner_id,owner_sku_code,name,uom,images) VALUES"
     "('AMZ2000003',1,'P005','x','件','{a,b,c,d,e,f}')", False),
    ("图片 5 张可建",
     "INSERT INTO sku(sku_id,owner_id,owner_sku_code,name,uom,images) VALUES"
     "('AMZ2000003',1,'P005','x','件','{a,b,c,d,e}')", True),
    ("重量为 0 被拒",
     "INSERT INTO sku(sku_id,owner_id,owner_sku_code,name,uom,weight_g) VALUES"
     "('AMZ2000004',1,'P006','x','件',0)", False),

    # ---- 待审版本 ----
    ("待审版本第一行",
     "INSERT INTO sku_pending(sku_id,name,uom) SELECT id,'洗发水新','件' "
     "FROM sku WHERE sku_id='AMZ2000001'", True),
    ("待审版本第二行被拒",
     "INSERT INTO sku_pending(sku_id,name,uom) SELECT id,'洗发水再','件' "
     "FROM sku WHERE sku_id='AMZ2000001'", False),

    # ---- 条码 ----
    ("条码第一条",
     "INSERT INTO sku_barcode(sku_id,owner_id,barcode,is_primary) "
     "SELECT id,1,'6901234567890',true FROM sku WHERE sku_id='AMZ2000001'", True),
    ("同货主条码重复被拒",
     "INSERT INTO sku_barcode(sku_id,owner_id,barcode) "
     "SELECT id,1,'6901234567890',false FROM sku WHERE sku_id='AMZ2000003'", False),
    ("跨货主同条码可建",
     "INSERT INTO sku_barcode(sku_id,owner_id,barcode) "
     "SELECT id,2,'6901234567890' FROM sku WHERE sku_id='UNL2000001'", True),
    ("同 SKU 第二个主条码被拒",
     "INSERT INTO sku_barcode(sku_id,owner_id,barcode,is_primary) "
     "SELECT id,1,'6901234567891',true FROM sku WHERE sku_id='AMZ2000001'", False),
    ("同 SKU 非主要条码可建",
     "INSERT INTO sku_barcode(sku_id,owner_id,barcode,pack_level) "
     "SELECT id,1,'6901234567891',2 FROM sku WHERE sku_id='AMZ2000001'", True),

    # ---- 包装层级 ----
    ("单品层正常",
     "INSERT INTO sku_pack(sku_id,level,unit_name,qty_per_parent) "
     "SELECT id,1,'件',1 FROM sku WHERE sku_id='AMZ2000001'", True),
    ("单品层填尺寸被拒",
     "INSERT INTO sku_pack(sku_id,level,unit_name,qty_per_parent,length_mm) "
     "SELECT id,1,'件',1,100 FROM sku WHERE sku_id='AMZ2000003'", False),
    ("内箱换算为 1 被拒",
     "INSERT INTO sku_pack(sku_id,level,unit_name,qty_per_parent,"
     "length_mm,width_mm,height_mm,weight_g) "
     "SELECT id,2,'内箱',1,300,200,150,500 FROM sku WHERE sku_id='AMZ2000001'", False),
    ("内箱缺尺寸被拒",
     "INSERT INTO sku_pack(sku_id,level,unit_name,qty_per_parent) "
     "SELECT id,2,'内箱',6 FROM sku WHERE sku_id='AMZ2000001'", False),
    ("内箱正常",
     "INSERT INTO sku_pack(sku_id,level,unit_name,qty_per_parent,"
     "length_mm,width_mm,height_mm,weight_g) "
     "SELECT id,2,'内箱',6,300,200,150,500 FROM sku WHERE sku_id='AMZ2000001'", True),
    ("外箱正常",
     "INSERT INTO sku_pack(sku_id,level,unit_name,qty_per_parent,"
     "length_mm,width_mm,height_mm,weight_g) "
     "SELECT id,3,'外箱',4,620,410,320,2100 FROM sku WHERE sku_id='AMZ2000001'", True),
    ("同 SKU 同层重复被拒",
     "INSERT INTO sku_pack(sku_id,level,unit_name,qty_per_parent,"
     "length_mm,width_mm,height_mm,weight_g) "
     "SELECT id,2,'内箱',8,300,200,150,600 FROM sku WHERE sku_id='AMZ2000001'", False),
    ("第 4 层被拒",
     "INSERT INTO sku_pack(sku_id,level,unit_name,qty_per_parent,"
     "length_mm,width_mm,height_mm,weight_g) "
     "SELECT id,4,'托盘',10,1000,800,900,8000 FROM sku WHERE sku_id='AMZ2000001'", False),

    # ---- 导入任务 ----
    ("导入任务 PENDING",
     "INSERT INTO sku_import_task(owner_id,file_name,file_key) VALUES(1,'a.xlsx','k1')", True),
    ("同货主第二个进行中任务被拒",
     "INSERT INTO sku_import_task(owner_id,file_name,file_key) VALUES(1,'b.xlsx','k2')", False),
    ("同货主已完成任务可再建",
     "INSERT INTO sku_import_task(owner_id,file_name,file_key,status) "
     "VALUES(1,'c.xlsx','k3','SUCCESS')", True),
    ("另一货主可并行",
     "INSERT INTO sku_import_task(owner_id,file_name,file_key) VALUES(2,'d.xlsx','k4')", True),

    # ---- 打印记录 ----
    ("打印 500 张",
     "INSERT INTO label_print_record(sku_id,owner_id,copies,channel,template_version,printed_by) "
     "SELECT id,1,500,'DOWNLOAD','v1',2 FROM sku WHERE sku_id='AMZ2000001'", True),
    ("打印 501 张被拒",
     "INSERT INTO label_print_record(sku_id,owner_id,copies,channel,template_version,printed_by) "
     "SELECT id,1,501,'DOWNLOAD','v1',2 FROM sku WHERE sku_id='AMZ2000001'", False),

    # ---- 字典 ----
    ("字典类型",
     "INSERT INTO sys_dict(dict_type,name) VALUES('owner_type','客户类型')", True),
    ("字典类型重复被拒",
     "INSERT INTO sys_dict(dict_type,name) VALUES('owner_type','客户类型2')", False),
    ("字典项",
     "INSERT INTO sys_dict_item(dict_type,label,value) VALUES('owner_type','品牌商','BRAND')", True),
    ("同类型同值重复被拒",
     "INSERT INTO sys_dict_item(dict_type,label,value) VALUES('owner_type','品牌商2','BRAND')", False),
    ("字典项引用不存在的类型被拒",
     "INSERT INTO sys_dict_item(dict_type,label,value) VALUES('nope','x','X')", False),

    # ---- 编码规则 ----
    ("编码规则",
     "INSERT INTO sys_code_rule(rule_code,name,prefix,seq_length) "
     "VALUES('TEST_RULE','测试编码规则','TEST',4)", True),
    ("编码规则重复被拒",
     "INSERT INTO sys_code_rule(rule_code,name,prefix,seq_length) "
     "VALUES('TEST_RULE','测试编码规则2','TEST',4)", False),
    ("流水位数超范围被拒",
     "INSERT INTO sys_code_rule(rule_code,name,prefix,seq_length) "
     "VALUES('TEST_SKU','商品编码','',13)", False),

    # ---- 审计日志 ----
    ("审计日志写入",
     "INSERT INTO sys_audit_log(biz_type,biz_id,action,operator_id,operator_name,summary) "
     "VALUES('SKU','AMZ2000001','UPDATE',2,'张三','改外箱箱规')", True),
]


def run(sql: str) -> tuple[int, str]:
    result = subprocess.run([*PSQL, sql], capture_output=True, text=True)
    lines = (result.stderr or "").strip().splitlines()
    return result.returncode, lines[0] if lines and result.returncode else "OK"


def main() -> int:
    for sql in SETUP:
        code, message = run(sql)
        if code != 0:
            print(f"准备数据失败：{message}")
            return 2

    passed = 0
    for name, sql, expect_ok in CASES:
        code, message = run(sql)
        ok = (code == 0) == expect_ok
        passed += ok
        mark = "✅" if ok else "❌"
        expect = "成功" if expect_ok else "被拒"
        actual = "成功" if code == 0 else "被拒"
        print(f"{mark} {name:<24} 期望{expect}  实际{actual}")
        if not ok:
            print(f"     {message}")

    print(f"\n{passed}/{len(CASES)} 通过")
    return 0 if passed == len(CASES) else 1


if __name__ == "__main__":
    sys.exit(main())
