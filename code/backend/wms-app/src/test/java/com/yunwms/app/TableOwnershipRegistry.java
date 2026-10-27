package com.yunwms.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeMap;

/**
 * 表归属清单 {@code table-ownership.json} 的内存模型：表名到归属模块的映射，加上已登记的跨模块例外。
 *
 * <p>为什么单独成类：清单解析与校验回答的是「清单本身可不可信」，与 Mapper 扫描回答的「代码有没有越界」
 * 是两件事，变化原因也不同。混在测试类里会让 {@link MapperTableAccessTest} 超过 {@code AGENTS.md}
 * 的 400 行改动上限，拆分后清单规则与用例断言各自能在自己那一屏里读完。
 *
 * <p>清单是门禁的唯一判据，因此结构错误一律当场失败：字段缺失、owner 取值非法、例外指向不存在的表、
 * 例外指向自己拥有的表，都说明清单已经不能表达真实归属，此时让测试通过只会让门禁变成摆设。
 */
final class TableOwnershipRegistry {

    /** 表归属清单的资源路径，文件位于 {@code wms-app/src/test/resources}。 */
    private static final String REGISTRY_RESOURCE = "/table-ownership.json";

    /** 清单中表示「归属未确认」的取值。它不是豁免：任何模块访问都会失败。 */
    private static final String UNASSIGNED = "unassigned";

    /** 后端模块清单，与 {@code code/backend/pom.xml} 的 {@code <modules>} 一致。 */
    private static final List<String> MODULES = List.of("wms-web", "wms-auth", "wms-master", "wms-app");

    /** 清单里允许登记的操作，与 {@link MapperTableAccessScanner} 的 operation 取值一一对应。 */
    private static final Set<String> KNOWN_OPERATIONS = Set.of("SELECT", "INSERT", "UPDATE", "DELETE");

    /** 表名到归属模块的有序映射。 */
    private final TreeMap<String, String> tables;

    /** 已登记的跨模块访问例外。 */
    private final List<TableException> exceptions;

    private TableOwnershipRegistry(TreeMap<String, String> tables, List<TableException> exceptions) {
        this.tables = tables;
        this.exceptions = exceptions;
    }

    /**
     * 读取并校验表归属清单。
     *
     * <p>校验不通过一律抛异常：清单写错时让测试通过，等于让门禁静默失效。
     *
     * @return 校验通过的清单
     * @throws IOException 读取资源失败
     */
    static TableOwnershipRegistry load() throws IOException {
        try (InputStream input = TableOwnershipRegistry.class.getResourceAsStream(REGISTRY_RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("classpath 上找不到 " + REGISTRY_RESOURCE + "，表归属无法校验");
            }
            JsonNode root = new ObjectMapper().readTree(input);
            TreeMap<String, String> owners = readTables(root.path("tables"));
            return new TableOwnershipRegistry(owners, readExceptions(root.path("exceptions"), owners));
        }
    }

    /**
     * 表名到归属模块的有序映射。
     *
     * @return 只读用途的映射，键为表名，值为归属模块或 {@code unassigned}
     */
    TreeMap<String, String> tables() {
        return tables;
    }

    /**
     * 已登记的跨模块访问例外。
     *
     * @return 例外列表，按清单中的顺序
     */
    List<TableException> exceptions() {
        return exceptions;
    }

    /**
     * 找出匹配本次访问的例外。
     *
     * <p>例外按「模块 + 表 + Mapper 文件」精确匹配：只按模块与表匹配会让同一模块里任何一个新 Mapper
     * 都能顺带用上这条例外，等于把单点债务变成模块通行证。
     *
     * @param module 访问方模块
     * @param table  被访问的表
     * @param mapper 访问发生的 Mapper 文件（相对模块根）
     * @return 命中的例外，没有则返回 null
     */
    TableException findException(String module, String table, String mapper) {
        for (TableException exception : exceptions) {
            if (exception.module().equals(module)
                    && exception.table().equals(table)
                    && exception.mapper().equals(mapper)) {
                return exception;
            }
        }
        return null;
    }

    /**
     * 读取并校验 `tables` 段。
     *
     * <p>只保留表名到归属模块的映射：`note` 是写给人看的归属理由，不参与判断，因此不进入内存模型——
     * 多存一份不读的字段，只会让「清单里改了什么」与「测试实际校验了什么」之间多出一层猜测。
     *
     * @param node `tables` 节点
     * @return 表名到归属模块的有序映射
     */
    private static TreeMap<String, String> readTables(JsonNode node) {
        if (!node.isObject() || node.isEmpty()) {
            throw new IllegalStateException("table-ownership.json 缺少非空的 tables 对象");
        }
        TreeMap<String, String> owners = new TreeMap<>();
        node.fields().forEachRemaining(entry -> {
            String table = entry.getKey();
            if (!table.matches("[a-z_][a-z0-9_]*")) {
                throw new IllegalStateException("表名 `" + table + "` 不是合法的 PostgreSQL 未加引号标识符（应为小写）");
            }
            String owner = requiredText(entry.getValue(), "owner", table);
            if (!MODULES.contains(owner) && !UNASSIGNED.equals(owner)) {
                throw new IllegalStateException("表 `" + table + "` 的 owner `" + owner + "` 非法，只能是 "
                        + MODULES + " 或 " + UNASSIGNED);
            }
            owners.put(table, owner);
        });
        return owners;
    }

    /**
     * 读取并校验 `exceptions` 段。
     *
     * @param node   `exceptions` 节点
     * @param tables 已校验的表归属（表名 → 归属模块）
     * @return 例外列表
     */
    private static List<TableException> readExceptions(JsonNode node, TreeMap<String, String> tables) {
        if (!node.isArray()) {
            throw new IllegalStateException("table-ownership.json 的 exceptions 必须是数组（没有例外时写空数组）");
        }
        List<TableException> exceptions = new ArrayList<>();
        for (JsonNode item : node) {
            String module = requiredText(item, "module", "exceptions");
            String table = requiredText(item, "table", "exceptions");
            String mapper = requiredText(item, "mapper", "exceptions");
            requiredText(item, "reason", "exceptions");

            if (!MODULES.contains(module)) {
                throw new IllegalStateException("例外登记的模块 `" + module + "` 不是后端模块，只能是 " + MODULES);
            }
            String owner = tables.get(table);
            if (owner == null) {
                throw new IllegalStateException("例外引用了清单里不存在的表 `" + table + "`");
            }
            if (module.equals(owner)) {
                throw new IllegalStateException("例外 `" + module + "` → `" + table
                        + "` 与表归属相同，这不是例外而是归属登记，必须删除");
            }
            if (!mapper.startsWith("src/main/resources/mapper/")) {
                throw new IllegalStateException("例外 `" + module + "` → `" + table + "` 的 mapper 必须是模块内相对路径，"
                        + "形如 src/main/resources/mapper/...，当前是 `" + mapper + "`");
            }
            // reason 只做非空校验（上面 requiredText），内容供人阅读，不参与判断
            exceptions.add(new TableException(module, table, mapper, readOperations(item.path("operations"))));
        }
        return exceptions;
    }

    /**
     * 读取例外允许的操作集合。
     *
     * @param node `operations` 节点
     * @return 大写操作名集合
     */
    private static Set<String> readOperations(JsonNode node) {
        if (!node.isArray() || node.isEmpty()) {
            throw new IllegalStateException("例外的 operations 必须是非空数组，取值见 " + KNOWN_OPERATIONS);
        }
        Set<String> operations = new LinkedHashSet<>();
        for (JsonNode operation : node) {
            String value = operation.asText().toUpperCase(Locale.ROOT);
            if (!KNOWN_OPERATIONS.contains(value)) {
                throw new IllegalStateException("例外登记的 operation `" + operation.asText()
                        + "` 非法，只能是 " + KNOWN_OPERATIONS);
            }
            operations.add(value);
        }
        return operations;
    }

    /**
     * 读取必需的非空字符串字段。
     *
     * @param node  所在节点
     * @param field 字段名
     * @param where 出错时用于定位的上下文
     * @return 字段值
     */
    private static String requiredText(JsonNode node, String field, String where) {
        JsonNode value = node.path(field);
        if (!value.isTextual() || value.asText().isBlank()) {
            throw new IllegalStateException("`" + where + "` 缺少非空字符串字段 `" + field + "`");
        }
        return value.asText();
    }

    /** 清单中登记的一条跨模块访问例外。 */
    record TableException(String module, String table, String mapper, Set<String> operations) {

        /**
         * 用于判断例外是否被命中过的键。
         *
         * @return `模块|表|Mapper 文件`
         */
        String key() {
            return module + "|" + table + "|" + mapper;
        }
    }
}
