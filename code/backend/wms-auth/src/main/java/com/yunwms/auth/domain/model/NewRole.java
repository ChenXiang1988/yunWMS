package com.yunwms.auth.domain.model;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * 待创建的角色。
 *
 * <p>只承载「创建角色」这一动作的入参，不承载 {@code builtin} 与 {@code status}：两者都不是管理员
 * 的可选项。
 * <ul>
 *   <li>{@code builtin} 恒为 {@code false}——接口不能创建内置角色，否则任何人都能造出一个
 *       「鉴权时动态展开为全部权限点」的超级管理员；</li>
 *   <li>{@code status} 固定为 {@code 'ACTIVE'}——停用是后续操作，不是创建时的选择。</li>
 * </ul>
 * 两个固定值由写入口 {@code RoleRegistrar} 显式落库，不依赖列默认值：默认值一旦被改动，
 * 建角色的语义不该跟着变。
 *
 * <p><b>数据范围是可选项，且默认值是保守的那一侧：</b>省略类型时为 {@link DataScopeType#CUSTOM}
 * 加空明细。选 {@code CUSTOM} 而不是 {@code ALL} 是因为两者选错的代价不对称——{@code CUSTOM}
 * 空明细的结果是「什么都看不见」，管理员会立刻发现并去配置；{@code ALL} 的结果是「全部可见」，
 * 可能几个月都没人注意到少配了一步。
 *
 * <p>类型与明细的搭配在这里判一次（非 {@code CUSTOM} 不得带明细）：这条约束不需要查库，
 * 属于聚合自身的不变量；「货主是否存在且启用」要跨模块查询，留在用例层。
 *
 * <p>编码格式规则定义在本类，是因为它属于角色这个聚合的不变量，而不是 HTTP 契约的一部分：
 * 将来的批量导入、初始化脚本同样要受它约束。正则与提示文案都以 {@code public static final}
 * 常量暴露，供接口层的 {@code @Pattern} 直接引用，保证规则只有一份定义。
 *
 * @param code          角色编码，需满足 {@link #CODE_PATTERN}；程序引用角色用，全局唯一
 * @param name          角色名称，给人看
 * @param remark        备注，可为 {@code null}
 * @param menuIds       勾选的菜单与权限点标识；空集合表示不授权任何权限，是合法状态
 * @param dataScopeType 数据范围类型，不能为 {@code null}（省略时由用例层补 {@code CUSTOM}）
 * @param ownerIds      {@code CUSTOM} 时的指定货主标识；其他类型必须为空集合
 */
public record NewRole(String code,
                      String name,
                      String remark,
                      Set<Long> menuIds,
                      DataScopeType dataScopeType,
                      Set<Long> ownerIds) {

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
     * @throws IllegalArgumentException 编码不符合格式、名称为空、数据范围类型为空，
     *                                  或非 {@code CUSTOM} 却带指定货主明细
     * @throws NullPointerException     {@code menuIds} 或 {@code ownerIds} 中含有 {@code null} 元素，
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
        if (dataScopeType == null) {
            throw new IllegalArgumentException("数据范围类型不能为空，应在用例层补默认值");
        }
        ownerIds = ownerIds == null ? Set.of() : Set.copyOf(ownerIds);
        if (!dataScopeType.usesOwnerDetails() && !ownerIds.isEmpty()) {
            // 与 sys_data_scope_owner 的注释同一口径：ALL 与 ASSIGNED 不落明细，
            // 带着明细建角色会写出一份「永远不生效」的数据，而界面上看不出它不生效
            throw new IllegalArgumentException("数据范围类型 " + dataScopeType.name() + " 不能指定货主明细");
        }
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
