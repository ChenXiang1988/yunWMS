package com.yunwms.master.domain.model;

import java.util.regex.Pattern;

/**
 * 货主主体的注册地址。
 *
 * <p><b>为什么把六个字段收成一个值对象：</b>「美国必须填州省」是一条跨字段规则，
 * 它既不属于 {@code country} 也不属于 {@code province}；放在 {@link Owner} 与 {@link NewOwner}
 * 各写一遍就是两处定义，而两处定义迟早只改一处。收成一个值对象后，创建与重建走同一个构造器，
 * 规则自然只有一份。这与 {@link SkuPrefix} 的处理一致。
 *
 * <p><b>这里只判格式，不判「是否取自字典」：</b>国家是不是字典里的启用项、州省是不是属于所选国家，
 * 需要查字典——聚合本身不持有字典，判定放在 {@code OwnerDictionaryValidator}。
 * 分开的判据是「聚合能不能自己回答」：两位大写的是格式，属于字典的事实则不是。
 *
 * <p>可选字段（{@code district}、{@code postalCode} 与除美国外的 {@code province}）
 * 原样保存，不做去空白或空串转 {@code null}：这类归一化一旦在领域层悄悄发生，
 * 「用户填了几个空格」与「用户没填」就再也分不开，而两者在审计里是不同的输入。
 *
 * @param country     注册国家，2 位大写字母的 ISO 3166-1 alpha-2 代码，对应 {@code COUNTRY} 字典的 value
 * @param province    州省，对应 {@code PROVINCE} 字典的 value（形如 {@code US-CA}）；美国必填
 * @param city        城市，必填
 * @param district    区县，可为 {@code null}
 * @param postalCode  邮政编码，可为 {@code null}
 * @param addressLine 详细地址，必填
 */
public record OwnerAddress(
        String country,
        String province,
        String city,
        String district,
        String postalCode,
        String addressLine) {

    /** 美国的国家代码。州省在该国必填，是规格里唯一一处按国家区分的必填规则。 */
    private static final String UNITED_STATES = "US";

    /**
     * 国家代码格式：两位大写字母。
     *
     * <p>与 {@code ck_owner_country} 的 {@code ^[A-Z]{2}$} 同口径。库里只拦写入，读出来的脏数据
     * 要在这里当场失败——小写的 {@code cn} 与 {@code CN} 是两个不同的字符串，
     * 一旦落库，按国家筛选时永远查不到它。
     */
    private static final Pattern COUNTRY_PATTERN = Pattern.compile("^[A-Z]{2}$");

    /**
     * 校验注册地址的不变量。
     *
     * @throws IllegalArgumentException 国家为空或格式不合规、美国缺州省、城市为空、详细地址为空
     */
    public OwnerAddress {
        if (country == null || country.isBlank()) {
            throw new IllegalArgumentException("注册国家不能为空");
        }
        if (!COUNTRY_PATTERN.matcher(country).matches()) {
            throw new IllegalArgumentException("注册国家必须是 2 位大写字母的国家代码，实际提交：" + country);
        }
        if (UNITED_STATES.equals(country) && (province == null || province.isBlank())) {
            throw new IllegalArgumentException("注册国家为美国时，州省为必填");
        }
        if (city == null || city.isBlank()) {
            throw new IllegalArgumentException("注册城市不能为空");
        }
        if (addressLine == null || addressLine.isBlank()) {
            throw new IllegalArgumentException("详细地址不能为空");
        }
    }
}
