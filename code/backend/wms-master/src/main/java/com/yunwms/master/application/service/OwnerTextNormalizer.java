package com.yunwms.master.application.service;

/**
 * 货主建档与编辑入参的文本归一化。
 *
 * <p><b>为什么单独成类，而不是在两个用例里各写一份：</b>{@code wms-auth} 的
 * {@code UserCreationService} / {@code UserQueryService} / {@code UserUpdateService} 各有一份
 * {@code stripToNull}，已被登记为该模块的存量债。再复制两份等于把同一件事的副本数从三变成五，
 * 而两份实现迟早对某个边界（全角空格、{@code \u00A0}）判得不一样。
 *
 * <p><b>为什么空串必须落成 {@code null}：</b>{@code ux_owner_external_code} 是部分唯一索引，
 * 条件是 {@code external_code IS NOT NULL}——空串不是 {@code NULL}，于是第二个「没填外部码」的货主
 * 会撞上第一个的空串，报「外部码已被占用」。省州的空串同理：它会被当成一个取值参与「是否属于该国」
 * 的判定，而「没填」与「填了个空」在业务上是同一件事。
 */
final class OwnerTextNormalizer {

    private OwnerTextNormalizer() {
    }

    /**
     * 去掉两侧空白，空串与全空白按未填处理。
     *
     * <p>只裁两侧、不动中间：{@code 深圳 某某} 里的那个空格是名称的一部分，去掉会改变用户提交的值。
     *
     * @param value 原始值，可为 {@code null}
     * @return 裁剪后的值；{@code null}、空串或全空白时为 {@code null}
     */
    static String toNullIfBlank(String value) {
        if (value == null) {
            return null;
        }
        String stripped = value.strip();
        return stripped.isEmpty() ? null : stripped;
    }
}
