package com.yunwms.app;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Mapper XML 表访问扫描器：从 {@code wms-auth} 与 {@code wms-master} 的 Mapper XML 抽出表访问，
 * 并从迁移脚本读出建表集合，供 {@link MapperTableAccessTest} 断言。
 *
 * <p>为什么单独成类：这两件事是门禁的支撑逻辑，与用例断言的变化原因不同——SQL 写法变才改这里，
 * 归属规则变才改测试。混在同一个文件里会让单文件逼近 {@code AGENTS.md} 的 400 行改动上限，
 * 拆分后两者都能在一屏内读完。
 *
 * <p>为什么扫描源码目录而不是 classpath：classpath 上的 XML 已经按 {@code mapper/auth}、{@code mapper/master} 分目录，
 * 但「文件来自哪个模块」只能靠目录名推断，这本身是个可被绕过的约定。直接从各模块的
 * {@code src/main/resources/mapper} 读文件，「哪个模块的 Mapper」由路径决定，不需要推断。
 * 代价是扫描依赖源码树存在，因此只能在 {@code wms-app} 的源码树里运行（见 {@link #backendRoot()}）。
 *
 * <p>刻意不做的事：不解析子查询结构、不维护完整的 SQL 语法树、不判断 JOIN 条件与过滤条件是否与归属方的业务口径一致。
 * 当前 SQL 简单（见 9 个 Mapper XML），正则够用；引入 SQL 解析库要新增依赖（{@code design.md} 已否决），
 * 而且解析器也回答不了「口径对不对」这种语义问题。这里只回答一个问题：<b>这张表归你管吗</b>。
 */
final class MapperTableAccessScanner {

    /** 迁移脚本目录（相对后端根），用于校验清单与建表集合一致。 */
    private static final String MIGRATION_DIR = "wms-app/src/main/resources/db/migration";

    /**
     * 参与扫描的模块及其 Mapper 根目录。
     *
     * <p>只扫 {@code wms-auth} 与 {@code wms-master}：{@code wms-web} 按定义不放持久化代码，
     * {@code wms-app} 只做装配。将来新增业务模块时，一边加模块一边在这里登记，否则新模块的 XML 不受检查。
     */
    private static final List<ScannedModule> SCANNED_MODULES = List.of(
            new ScannedModule("wms-auth", "src/main/resources/mapper"),
            new ScannedModule("wms-master", "src/main/resources/mapper"));

    /** XML 注释先剥掉：注释里出现的 `from xxx` 是在解释设计，不是在读表。 */
    private static final Pattern XML_COMMENT = Pattern.compile("<!--.*?-->", Pattern.DOTALL);

    /** 再剥掉标签：`<update id="updateStatus">` 的 `id` 属性否则会被当成表名 `id`。 */
    private static final Pattern XML_TAG = Pattern.compile("<[^>]*>", Pattern.DOTALL);

    /** 单条语句里的第一个 CTE：`WITH target AS (`。 */
    private static final Pattern CTE_HEAD = sqlPattern(
            "\\bwith\\s+(?:recursive\\s+)?([a-z_][a-z0-9_]*)\\s+as\\s*\\(");

    /** 同一条语句里后续的 CTE：`) , next_cte AS (`。 */
    private static final Pattern CTE_TAIL = sqlPattern("\\)\\s*,\\s*([a-z_][a-z0-9_]*)\\s+as\\s*\\(");

    /**
     * 表访问的识别模式。
     *
     * <p>`JOIN (` 这类派生表不会被匹配：表名位置要求紧跟标识符，而左括号不是标识符字符。
     * 同理 `FROM (SELECT ...)` 也不会误报。
     */
    private static final List<TablePattern> TABLE_PATTERNS = List.of(
            new TablePattern("SELECT", sqlPattern("\\b(?:from|join)\\s+([a-z_][a-z0-9_]*)")),
            new TablePattern("INSERT", sqlPattern("\\binsert\\s+into\\s+([a-z_][a-z0-9_]*)")),
            new TablePattern("UPDATE", sqlPattern("\\bupdate\\s+([a-z_][a-z0-9_]*)")),
            new TablePattern("DELETE", sqlPattern("\\bdelete\\s+from\\s+([a-z_][a-z0-9_]*)")));

    /** 迁移脚本里的建表语句，允许 `IF NOT EXISTS`。 */
    private static final Pattern CREATE_TABLE = sqlPattern(
            "\\bcreate\\s+table\\s+(?:if\\s+not\\s+exists\\s+)?([a-z_][a-z0-9_]*)");

    /**
     * 不是表访问的子句：行锁 `FOR UPDATE` 与 upsert 的 `DO UPDATE`。
     *
     * <p>不剥掉它们会得到假表名：`FOR UPDATE` 之后若还跟着一条 `UPDATE owner`（本仓库的
     * {@code selectByIdForUpdate} 与 {@code updateProfile} 正好相邻），{@code \bupdate\s+(\w+)}
     * 会从行锁的 `UPDATE` 开始跨越换行匹配，把下一行的 `UPDATE` 当成表名。
     */
    private static final Pattern NON_TABLE_CLAUSE = sqlPattern("\\b(?:for\\s+update|do\\s+update)\\b");

    /** SQL 行注释：被注释掉的建表语句不算表存在。 */
    private static final Pattern SQL_LINE_COMMENT = Pattern.compile("--[^\\n]*");

    private MapperTableAccessScanner() {
    }

    /**
     * 编译一个 SQL 关键字模式。
     *
     * <p>必须带 {@link Pattern#CASE_INSENSITIVE}：手写 SQL 的关键字一律大写（{@code FROM}、{@code UPDATE}），
     * 而模式为可读性用小写书写。少了这个标志，模式一条都匹配不上——测试会以「抽取结果为空」的形式失败，
     * 看起来像扫描目录出了问题，实际是标志漏了。
     *
     * @param regex 小写书写的模式
     * @return 大小写不敏感的模式
     */
    private static Pattern sqlPattern(String regex) {
        return Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
    }

    /**
     * 从参与扫描的模块里读出全部 Mapper XML 并抽出表访问。
     *
     * <p>按模块、按路径排序后再解析，让失败信息与运行顺序稳定：同一份代码两次运行必须报出同一份违规列表，
     * 否则排查时会怀疑是随机失败。
     *
     * @return 表访问列表，按模块与文件路径有序
     * @throws IOException 读取 XML 失败
     */
    static List<TableAccess> scanMappers() throws IOException {
        List<TableAccess> accesses = new ArrayList<>();
        for (ScannedModule scannedModule : SCANNED_MODULES) {
            Path moduleRoot = backendRoot().resolve(scannedModule.name());
            Path mapperRoot = moduleRoot.resolve(scannedModule.mapperRoot());
            if (!Files.isDirectory(mapperRoot)) {
                throw new IllegalStateException("找不到模块 " + scannedModule.name() + " 的 Mapper 目录：" + mapperRoot);
            }
            for (Path xml : filesUnder(mapperRoot, ".xml")) {
                String mapper = toUnixPath(moduleRoot.relativize(xml));
                String content = Files.readString(xml, StandardCharsets.UTF_8);
                accesses.addAll(extractTableAccesses(scannedModule.name(), mapper, content));
            }
        }
        return accesses;
    }

    /**
     * 从一个 Mapper XML 的文本中抽出全部表访问。
     *
     * @param module  模块名，例如 {@code wms-auth}
     * @param mapper  Mapper 文件相对模块根的路径，例如 {@code src/main/resources/mapper/auth/SysUserMapper.xml}
     * @param content XML 原文
     * @return 表访问列表，按出现顺序
     */
    private static List<TableAccess> extractTableAccesses(String module, String mapper, String content) {
        String sql = stripMarkup(content);
        Set<String> cteNames = cteNames(sql);

        List<TableAccess> accesses = new ArrayList<>();
        for (TablePattern tablePattern : TABLE_PATTERNS) {
            Matcher matcher = tablePattern.pattern().matcher(sql);
            while (matcher.find()) {
                String table = matcher.group(1).toLowerCase(Locale.ROOT);
                // CTE 名不是表：`WITH target AS (...) UPDATE sys_user ... FROM target` 里的 target 是本次语句的中间结果
                if (cteNames.contains(table)) {
                    continue;
                }
                accesses.add(new TableAccess(module, mapper, tablePattern.operation(), table));
            }
        }
        return accesses;
    }

    /**
     * 剥掉 XML 注释、标签与不是表访问的子句，只留可扫描的 SQL 文本。
     *
     * <p>顺序不能反：注释里可能含 `>`，先剥标签会把注释切成碎片留下来，碎片里的 `from` 就成了假表名。
     *
     * @param xml Mapper XML 原文
     * @return 只剩 SQL 文本的字符串，每个被剥掉的位置替换为空格以免两个词粘成一个
     */
    private static String stripMarkup(String xml) {
        String withoutComments = XML_COMMENT.matcher(xml).replaceAll(" ");
        String withoutTags = XML_TAG.matcher(withoutComments).replaceAll(" ");
        return NON_TABLE_CLAUSE.matcher(withoutTags).replaceAll(" ");
    }

    /**
     * 取出语句中定义的 CTE 名。
     *
     * @param sql 已剥离标签的 SQL 文本
     * @return 小写 CTE 名集合
     */
    private static Set<String> cteNames(String sql) {
        Set<String> names = new LinkedHashSet<>();
        Matcher head = CTE_HEAD.matcher(sql);
        while (head.find()) {
            names.add(head.group(1).toLowerCase(Locale.ROOT));
        }
        Matcher tail = CTE_TAIL.matcher(sql);
        while (tail.find()) {
            names.add(tail.group(1).toLowerCase(Locale.ROOT));
        }
        return names;
    }

    /**
     * 读出迁移脚本定义的全部表名。
     *
     * <p>为什么也要校验这一条：清单只被 Mapper 扫描校验时，没有 Mapper 的表永远不会被登记，
     * 清单会落后于数据库。落后的清单在下次出现跨模块访问时给出的是「表未登记」而不是
     * 「表归属谁、这次越界了」，排查成本从一眼变成一次考古。
     *
     * @return 小写表名集合
     * @throws IOException 读取迁移脚本失败
     */
    static Set<String> migrationTables() throws IOException {
        Path migrationDir = backendRoot().resolve(MIGRATION_DIR);
        if (!Files.isDirectory(migrationDir)) {
            throw new IllegalStateException("找不到迁移目录：" + migrationDir);
        }
        Set<String> tables = new TreeSet<>();
        for (Path sqlFile : filesUnder(migrationDir, ".sql")) {
            String sql = SQL_LINE_COMMENT.matcher(Files.readString(sqlFile, StandardCharsets.UTF_8)).replaceAll(" ");
            Matcher matcher = CREATE_TABLE.matcher(sql);
            while (matcher.find()) {
                tables.add(matcher.group(1).toLowerCase(Locale.ROOT));
            }
        }
        return tables;
    }

    /**
     * 列出目录下指定后缀的普通文件，按路径排序。
     *
     * @param root      目录
     * @param extension 文件后缀，含点
     * @return 文件路径列表
     * @throws IOException 遍历失败
     */
    private static List<Path> filesUnder(Path root, String extension) throws IOException {
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(extension))
                    .sorted()
                    .toList();
        }
    }

    /**
     * 定位后端 Maven 根目录（{@code code/backend}）。
     *
     * <p>从当前工作目录向上找：Surefire 的工作目录是模块目录 {@code wms-app}，而扫描对象在兄弟模块里，
     * 所以必须回到根目录再往下走。找不到就失败而不是猜一个相对路径——猜错的表现是「扫描不到文件 →
     * 断言空转通过」，正好是本测试要防的那种静默失败。
     *
     * @return 后端根目录
     */
    private static Path backendRoot() {
        Path start = Path.of("").toAbsolutePath();
        Path candidate = start;
        for (int depth = 0; depth < 4 && candidate != null; depth++) {
            if (isBackendRoot(candidate)) {
                return candidate;
            }
            // 从仓库根启动的场景（IDE 默认工作目录）：后端不在当前目录，在 code/backend 下
            if (isBackendRoot(candidate.resolve("code/backend"))) {
                return candidate.resolve("code/backend");
            }
            candidate = candidate.getParent();
        }
        throw new IllegalStateException("从 " + start + " 向上找不到后端根目录（需同时存在 "
                + SCANNED_MODULES.get(0).name() + "、" + SCANNED_MODULES.get(1).name() + " 与 " + MIGRATION_DIR + "）");
    }

    /**
     * 判断一个目录是不是后端 Maven 根目录。
     *
     * <p>判据是「两个被扫描模块的 Mapper 目录 + 迁移目录同时存在」：只判断其中一个会在同名目录误命中，
     * 而误命中之后的表现是扫描不到文件、断言空转通过。
     *
     * @param candidate 待判断目录
     * @return 是后端根目录时返回 true
     */
    private static boolean isBackendRoot(Path candidate) {
        return Files.isDirectory(candidate.resolve(SCANNED_MODULES.get(0).name()))
                && Files.isDirectory(candidate.resolve(SCANNED_MODULES.get(1).name()))
                && Files.isDirectory(candidate.resolve(MIGRATION_DIR));
    }

    /**
     * 把路径转成 `a/b/c` 形式的字符串，供跨平台比对与失败信息使用。
     *
     * @param path 路径
     * @return 以 `/` 分隔的相对路径
     */
    private static String toUnixPath(Path path) {
        return path.toString().replace('\\', '/');
    }

    /** 参与扫描的模块。 */
    private record ScannedModule(String name, String mapperRoot) {
    }

    /** 表访问的识别模式：`operation` 是该模式对应的写读语义。 */
    private record TablePattern(String operation, Pattern pattern) {
    }

    /** 一次表访问。 */
    record TableAccess(String module, String mapper, String operation, String table) {

        /**
         * 失败信息里的定位串。
         *
         * @return `模块:Mapper 文件 操作 表名`
         */
        String location() {
            return module + ":" + mapper + " " + operation + " " + table;
        }
    }
}
