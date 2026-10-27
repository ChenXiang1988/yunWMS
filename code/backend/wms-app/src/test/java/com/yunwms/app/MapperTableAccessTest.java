package com.yunwms.app;

import com.yunwms.app.MapperTableAccessScanner.TableAccess;
import com.yunwms.app.TableOwnershipRegistry.TableException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mapper 表访问门禁：{@code wms-auth} 与 {@code wms-master} 的 Mapper XML 只能访问本模块拥有的表，或清单里登记过例外的表。
 *
 * <p>为什么需要这条测试：{@link com.yunwms.app.ModuleBoundaryTest} 只分析 Java 字节码，而跨模块访问数据库
 * 根本不经过 Java 引用——{@code wms-auth} 在 XML 里写一句 {@code FROM owner} 就能读到 {@code wms-master} 的表，
 * 编译、单测、模块边界规则全都不响。审计结论（{@code openspec/changes/archive/2026-10-27-audit-architecture-boundaries/audit.md}
 * 第 3.3 节）确认 {@code OwnerNameMapper.xml} 曾经就是这样一个既成事实：它绕过了 wms-master 的货主规则，
 * 也因为没有任何门禁可以复制到第二处、第三处。该 Mapper 与它的例外已删除，本门禁因此成为
 * 「auth 再出现 owner 的 SQL 就当场失败」的唯一手段。
 *
 * <p>失效模式与 import 越界不同：读表越界不会在被读方留下任何痕迹，被读方的字段改名、加 {@code deleted_at}
 * 条件、拆表都不会通知到越界方——错误会以「另一个模块的页面数据不对」的形式在很久之后出现，
 * 而这时没人会想到去看别的模块的 XML。
 *
 * <p>做法是「清单 + 轻量扫描」（{@code design.md} D3）：{@code table-ownership.json} 记录每张表的归属模块，
 * 本测试从 XML 里抽出 {@code FROM} / {@code JOIN} / {@code INSERT INTO} / {@code UPDATE} / {@code DELETE FROM}
 * 后面的表名，要求表归属等于本模块，或命中登记过的例外。<b>未登记的表名一律失败</b>——不认识不等于没问题，
 * 恰恰相反，不认识是本测试唯一能给出的、有价值的信号。
 *
 * <p>本类只保留用例与断言：清单解析与校验见 {@link TableOwnershipRegistry}，SQL 抽取与源码树定位见
 * {@link MapperTableAccessScanner}。拆分理由是支撑逻辑与断言的变化原因不同，且单文件需要留在
 * {@code AGENTS.md} 的 400 行改动上限之内。
 */
@DisplayName("Mapper 表访问边界")
class MapperTableAccessTest {

    @Test
    @DisplayName("Mapper XML 只访问本模块拥有或已登记例外的表")
    void mapperXmlShouldOnlyAccessPermittedTables() throws IOException {
        TableOwnershipRegistry registry = TableOwnershipRegistry.load();
        List<TableAccess> accesses = MapperTableAccessScanner.scanMappers();
        // 抽不到任何表访问说明正则或目录结构失效了，此时下面的断言会空转通过——必须先把这种情况判死
        assertThat(accesses)
                .as("没有从任何 Mapper XML 中提取到表访问，扫描逻辑或目录结构已失效，本测试会空跑")
                .isNotEmpty();

        List<String> violations = new ArrayList<>();
        Set<String> usedExceptions = new LinkedHashSet<>();
        for (TableAccess access : accesses) {
            String owner = registry.tables().get(access.table());
            if (owner == null) {
                violations.add(access.location() + "：表 `" + access.table()
                        + "` 未在 table-ownership.json 登记；新表必须同时登记归属模块");
                continue;
            }
            if (access.module().equals(owner)) {
                continue;
            }
            TableException exception = registry.findException(access.module(), access.table(), access.mapper());
            if (exception == null) {
                violations.add(access.location() + "：表 `" + access.table() + "` 归属 " + owner
                        + "，跨模块访问必须先在 table-ownership.json 登记例外并写明原因");
                continue;
            }
            usedExceptions.add(exception.key());
            if (!exception.operations().contains(access.operation())) {
                violations.add(access.location() + "：表 `" + access.table() + "` 的已登记例外只允许 "
                        + exception.operations() + "，本次是 " + access.operation());
            }
        }

        assertThat(violations)
                .as("Mapper 越界访问了不属于本模块的表：读越界让模块绕过归属方的业务规则直接取数，"
                        + "且被读方改名、拆表、加软删除条件时不会有任何编译期信号")
                .isEmpty();

        List<String> deadExceptions = registry.exceptions().stream()
                .filter(exception -> !usedExceptions.contains(exception.key()))
                .map(exception -> exception.key() + "（" + exception.mapper() + "）")
                .toList();
        assertThat(deadExceptions)
                .as("以下例外没有任何 Mapper XML 命中：债务已经消失或清单写错了路径。例外是债务清单，不是豁免，"
                        + "失效的条目必须删除，否则它会掩盖下一次真实的越界")
                .isEmpty();
    }

    @Test
    @DisplayName("表归属清单与迁移脚本的建表集合一致")
    void registryShouldCoverEveryMigrationTable() throws IOException {
        TableOwnershipRegistry registry = TableOwnershipRegistry.load();
        Set<String> migrated = MapperTableAccessScanner.migrationTables();
        assertThat(migrated).as("没有从迁移脚本读到任何建表语句，扫描逻辑或目录结构已失效").isNotEmpty();

        Set<String> unregistered = new TreeSet<>(migrated);
        unregistered.removeAll(registry.tables().keySet());
        assertThat(unregistered)
                .as("以下表在迁移脚本里存在但清单未登记归属：新表必须同时登记 owner（无法确认归属时登记 unassigned 并写明原因）")
                .isEmpty();

        Set<String> unknown = new TreeSet<>(registry.tables().keySet());
        unknown.removeAll(migrated);
        assertThat(unknown)
                .as("清单登记了迁移脚本里不存在的表：表被删或名字写错，过时的条目必须删除")
                .isEmpty();
    }
}
