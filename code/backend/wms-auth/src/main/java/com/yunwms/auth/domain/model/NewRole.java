package com.yunwms.auth.domain.model;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * 待创建的角色。
 *
 * <p>只承载「创建角色」这一动作的入参，不承载 {@code builtin}、{@code data_scope_type} 与
 * {@code status}：三者都不是管理员的可选项。
 * <ul>
 *   <li>{@code builtin} 恒为 {@code false}——接口不能创建内置角色，否则任何人都能造出一个
 *       「鉴权时动态展开为全部权限点」的超级管理员；</li>
 *   <li>{@code data_scope_type} 固定为 {@code 'CUSTOM'}——数据范围配置属于 2.4，在它落地前
 *       新建角色的数据范围是「空明细」，保守方向是空集（无权）而不是「全部货主」；</li>
 *   <li>{@code status} 固定为 {@code 'ACTIVE'}——停用是后续操作，不是创建时的选择。</li>
 * </ul>
 * 三个固定值由写入口 {@code RoleRegistrar} 显式落库，不依赖列默认值：默认值一旦被改动，
 * 建角色的语义不该跟着变。
 *
 * <p>编码格式规则定义在本类，是因为它属于角色这个聚合的不变量，而不是 HTTP 契约的一部分：
 * 将来的批量导入、初始化脚本同样要受它约束。正则与提示文案都以 {@code public static final}
 * 常量暴露，供接口层的 {@code @Pattern} 直接引用，保证规则只有一份定义。
 *
 * @param code    角色编码，需满足 {@link #CODE_PATTERN}；程序引用角色用，全局唯一
 * @param name    角色名称，给人看
 * @param remark  备注，可为 {@code null}
 * @param menuIds 勾选的菜单与权限点标识；空集合表示不授权任何权限，是合法状态
 */
public record NewRole(String code, String name, String remark, Set<Long> menuIds) {

    /**
     * 角色编码格式：大写字母开头，只含大写字母、数字与下划线，全长 2 到 32 位。
     *
     * <p>不含小写字母是刻意的：权限点标识（{@code system:role:create}）与角色编码都会被人拿来
     * 拼在配置与日志里，允许大小写混用会得到「看起来一样、实际不同」的两个编码。
     *
     * <p>声明为编译期常量，让接口层的 {@code @Pattern(regexp = ...)} 引用同一份定义。
     */
    public static final String CODE_PATTERN = "^[A-Z][A-Z0-9_]{1,31}$";

    /** 编码不合规时给管理员看的话术；与 {@link #CODE_PATTERN} 描述同一条规则。 */
    public static final String CODE_FORMAT_HINT = "角色编码需以大写字母开头，只能包含大写字母、数字与下划线，长度 2 到 32 位";

    private static final Pattern CODE_MATCHER = Pattern.compile(CODE_PATTERN);

    /**
     * 校验并规范化创建期不变量。
     *
     * <p>拒绝非法值是最后一道防线：用例层会先判一次并给出可展示的业务错误，
     * 这里是防止绕过用例直接构造（例如新的导入入口）时把脏数据写下去。
     *
     * @throws IllegalArgumentException 编码不符合格式，或名称为空
     * @throws NullPointerException     {@code menuIds} 中含有 {@code null} 元素，
     *                                  属于调用方编程错误，不包装成业务错误
     */
    public NewRole {
        if (!isWellFormedCode(code)) {
            throw new IllegalArgumentException("角色编码不合法：" + code);
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("角色名称不能为空");
        }
        // 集合语义：同一 id 传两次不该变成主键冲突；用不可变集合同时挡住事后篡改
        menuIds = menuIds == null ? Set.of() : Set.copyOf(menuIds);
    }

    /**
     * 判断编码是否满足 {@link #CODE_PATTERN}。
     *
     * <p>不做大小写转换也不裁剪空白：静默把 {@code cs_staff} 改成 {@code CS_STAFF} 会让管理员
     * 以为自己输入的是别的编码，而编码是程序引用的标识，改动必须由人确认。
     *
     * @param code 待判定的编码，可为 {@code null}
     * @return 合规返回 {@code true}；{@code null} 或不匹配返回 {@code false}
     */
    public static boolean isWellFormedCode(String code) {
        return code != null && CODE_MATCHER.matcher(code).matches();
    }
}
