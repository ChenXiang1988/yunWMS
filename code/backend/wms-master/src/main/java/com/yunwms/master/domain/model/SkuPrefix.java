package com.yunwms.master.domain.model;

import java.util.regex.Pattern;

/**
 * 货主的 SKU 前缀：4 位大写字母或数字，排除易混淆字符 {@code 0}、{@code O}、{@code 1}、{@code I}。
 *
 * <p><b>为什么做成值对象而不是在某个类里做一次 {@code if}：</b>前缀有两条入口——建档时的
 * 用户输入（{@link NewOwner}）与从库里重建（{@link Owner}）——两条入口都必须受同一条规则约束。
 * 规则写在其中一个类上、另一个类调用它，读代码的人就要靠记忆确认「另一条路径有没有校验」；
 * 写成类型后，任何持有前缀的代码都只可能拿到已校验的值，绕过校验需要用 {@code null} 或反射。
 * 这与 {@code UserAccount} 用紧凑构造器守住「货主账号必须绑定货主」是同一个手法。
 *
 * <p><b>不排除 {@code 0}、{@code O}、{@code 1}、{@code I} 的原因：</b>这四个字符在手写的货位标签与
 * 拣货单上最容易认错（{@code 0}/{@code O}、{@code 1}/{@code I} 在多数打印字体里只差一个笔画），
 * 而前缀会直接成为 SKUID 的前四位并被贴在实物上，认错一位就是另一个货主的货。
 *
 * <p><b>为什么不自动把 {@code O} 换成 {@code 0}：</b>规格要求系统拒绝含被排除字符的输入、
 * MUST NOT 自动替换。自动替换会让用户以为库里存的是自己填的那个字符，实际不是；
 * 等到打印出的标签与手上记的不一致时，已经无法判断是录入错还是系统改的。
 * 同理，这里也不做去空白、不做大小写转换：{@code abcd} 与 {@code ABCD} 是两个输入，
 * 静默改成后者等于替用户做了决定。
 *
 * <p>前缀全局唯一，且创建后不可修改（草稿状态除外）。唯一性由 {@code ux_owner_sku_prefix}
 * 全表唯一索引保证，本类型只负责格式。
 *
 * @param value 前缀原文，必须是 4 位大写字母或数字且不含被排除字符
 */
public record SkuPrefix(String value) {

    /**
     * 格式不合规时给用户看的话术。
     *
     * <p>声明为常量供接口层（3.1b）的校验注解与提示复用：规则只有一份定义，
     * 改口径时不会出现「正则改了、文案没改」。
     */
    public static final String FORMAT_HINT = "SKU 前缀只能是 4 位大写字母或数字，且不能包含 0、O、1、I";

    /**
     * 格式：{@code A-H} + {@code J-N} + {@code P-Z} + {@code 2-9}。
     *
     * <p>写成字符区间的并集而不是「先匹配 {@code [A-Z0-9]{4}} 再逐个排除」：一条正则就能看出
     * 到底排除了什么，而两段式判定在改动时容易只改一处。
     */
    private static final Pattern PATTERN = Pattern.compile("^[A-HJ-NP-Z2-9]{4}$");

    /**
     * 校验格式。
     *
     * <p>提示里带上实际提交的值（原文，不做任何替换）：「你填的不对」不告诉对方填了什么，
     * 在录入方看来与「系统坏了」没有区别。
     *
     * @throws IllegalArgumentException 前缀为 {@code null} 或不满足 {@link #PATTERN}
     */
    public SkuPrefix {
        if (value == null || !PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException(FORMAT_HINT + "，实际提交：" + value);
        }
    }
}
