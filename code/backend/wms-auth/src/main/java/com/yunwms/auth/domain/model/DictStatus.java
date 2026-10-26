package com.yunwms.auth.domain.model;

/**
 * 字典与字典项的启用状态。
 *
 * <p>同一个枚举同时用于 {@code sys_dict} 与 {@code sys_dict_item}：两张表的列约束取值完全相同
 * （{@code CHECK (status IN ('ACTIVE', 'DISABLED'))}），语义也是同一个——「能不能被新数据引用」。
 * 拆成两个等价枚举只会让调用方多一次转换，而转换的地方就是漏判的地方。
 *
 * <p>停用不是删除：行仍在库里，引用过它的业务数据照常展示。两种消费语义靠调用方传参区分
 * （下拉只要 {@link #ACTIVE}，历史回显要连 {@link #DISABLED} 一起取），不靠两个端点。
 *
 * <p>数据库存 {@link #name()}，读取到未知编码时失败：库里出现 {@code 'FOO'} 说明数据被绕过
 * CHECK 写坏，静默按启用处理会让一个本该停用的项重新出现在下拉里。
 */
public enum DictStatus {

    /** 启用：可被新数据引用，出现在下拉列表中。 */
    ACTIVE,

    /** 停用：不再出现在下拉列表中，但历史数据仍按它展示。 */
    DISABLED;

    /**
     * 按数据库中的编码还原。
     *
     * @param code 状态编码，取值为枚举名
     * @return 对应的状态
     * @throws IllegalArgumentException 编码未知，说明数据已损坏，不静默回落
     */
    public static DictStatus fromCode(String code) {
        for (DictStatus status : values()) {
            if (status.name().equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("未知的字典状态编码：" + code);
    }
}
