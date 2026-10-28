package com.yunwms.master.domain.model;

import java.util.regex.Pattern;

/**
 * 货主税务登记，承载从库中读到的既成事实。
 *
 * <p><b>为什么与 {@link OwnerTaxDraft} 分成两个类型：</b>标识与归属只在落库之后才存在。用一个
 * {@code id=0}、{@code ownerId=0} 的实例冒充「待登记」，读代码的人无法从签名上分辨「某货主名下
 * 已登记的税号」与「即将登记的一组字段值」，也会诱使后续代码直接拿它去更新而不校验它有没有落库。
 * 这与 {@link Owner}/{@link NewOwner}、{@link OwnerContact}/{@link OwnerContactDraft} 是同一条理由。
 *
 * <p><b>这里为什么没有审计四列：</b>{@code created_by} / {@code updated_by} 由仓储在写入时维护，
 * 而税号页签展示国家、州省、类型与号码，不展示「谁改的」。搬进来会让本类型成为审计快照的第二个
 * 来源（子表变更留痕由规格要求以「完整集合快照」承担），且加的时候所有构造点一起编译失败，漏不掉。
 *
 * <p><b>重建路径同样校验：</b>紧凑构造器调用 {@link #requireFields}，与 {@link OwnerTaxDraft} 用的是
 * 同一份实现——创建期被拒的组合，重建期也不会被放行。{@code ck_tax_country} 与列宽是最后一道防线，
 * 拦的是写入；被绕过约束写坏的行只能在读出来的那一刻被发现。
 *
 * <p><b>为什么只判国家形态，不判税号格式：</b>两位大写字母由 {@code ck_tax_country} 直接约束，与业务
 * 语义无关，可以在类型上判死。税号格式（如中国统一社会信用代码 18 位）随 {@code TAX_ID_TYPE} 字典项
 * 而变，判它先得知道「这一项是什么语义」，那是用例层查字典之后的判断（3.6 的用例）；写在这里会让
 * 「能不能存」与「格式对不对」共用一个抛点。
 *
 * <p>本类型不提供任何改法：编辑走 {@link OwnerTaxDraft} 加仓储的 {@code update}，新值是一次写入，
 * 这样「谁在什么时候换掉了默认税号」留在 {@code updated_by}/{@code updated_at} 上。「同一货主同一国家
 * 至多一条默认」不在这里判——它是 {@code ux_owner_tax_default} 的职责，见 {@code OwnerTaxRepository}。
 *
 * @param id        标识，数据库生成
 * @param ownerId   所属货主标识；税号不是独立聚合，归属创建后不可修改
 * @param country   国家，两位大写字母（ISO 3166-1 alpha-2），与 {@code ck_tax_country} 同口径
 * @param province  州省，可为 {@code null}，最长 64 字符；国家级税号（如统一社会信用代码）没有州省
 * @param taxType   税号类型，取 {@code TAX_ID_TYPE} 字典的 value，如 {@code US_EIN}，最长 20 字符
 * @param taxNumber 税号，必填，最长 64 字符
 * @param isDefault 是否该货主在该国家下的默认税号；同一货主同一国家至多一条，由部分唯一索引保证
 */
public record OwnerTaxRegistration(
        long id,
        long ownerId,
        String country,
        String province,
        String taxType,
        String taxNumber,
        boolean isDefault) {

    /**
     * 国家码的形态：两位大写字母。
     *
     * <p>与 {@code V009} 的 {@code ck_tax_country}（{@code country ~ '^[A-Z]{2}$'}）逐字一致。写成同一个
     * 正则而不是「长度 2 且全为字母」之类的等价判断：库与模型用不同写法时，两边迟早对
     * 「小写算不算」给出不同答案。
     */
    private static final Pattern COUNTRY_PATTERN = Pattern.compile("[A-Z]{2}");

    /**
     * 校验读到的事实。
     *
     * @throws IllegalArgumentException 国家不是两位大写字母，或税号类型/税号为空、任一字段超出列宽
     */
    public OwnerTaxRegistration {
        requireFields(country, province, taxType, taxNumber);
    }

    /**
     * 校验税务登记的字段不变量，创建（{@link OwnerTaxDraft}）与重建（本记录）共用这一份实现。
     *
     * <p>包内可见而不是 {@code public}：规则一旦有公开入口，就会有人拿它当「校验工具」在别处调用，
     * 而它只判这四个文本字段——既不判税号类型是否属于字典（要查字典，属用例层），也不判默认税号
     * 是否已存在（属数据库唯一索引）。这与 {@link OwnerContact#requireFields} 一致。
     *
     * <p>宽度取自 {@code V009} 的列定义：改列宽时只改这一处，否则「创建放过、重建拦下」的错位
     * 会一直没有人发现。
     *
     * @param country   国家，必填
     * @param province  州省，可为 {@code null}
     * @param taxType   税号类型，必填
     * @param taxNumber 税号，必填
     * @throws IllegalArgumentException 国家不是两位大写字母，或税号类型/税号为空、任一字段超出列宽
     */
    static void requireFields(String country, String province, String taxType, String taxNumber) {
        requireCountry(country);
        requirePresent(taxType, "税号类型", 20);
        requirePresent(taxNumber, "税号", 64);
        requireMaxLength(province, "州省", 64);
    }

    /**
     * 校验国家是两位大写字母。
     *
     * <p>先判空白再判形态：{@code null} 与空串直接进正则，报出来的是「不是两位大写字母」，读的人
     * 要再想一步才知道其实是没填。不做大小写归一（{@code "us"} 不转成 {@code "US"}）：库里存的是提交
     * 上来的原值，领域层悄悄改写会让审计里「提交的值」与「库里的值」对不上。
     *
     * @param country 国家
     * @throws IllegalArgumentException 国家为 {@code null}、空白，或不是两位大写字母
     */
    private static void requireCountry(String country) {
        if (country == null || country.isBlank()) {
            throw new IllegalArgumentException("国家不能为空");
        }
        if (!COUNTRY_PATTERN.matcher(country).matches()) {
            throw new IllegalArgumentException("国家必须是 2 位大写字母，实际：" + country);
        }
    }

    /**
     * 校验必填字段非空白且不超长。
     *
     * <p>判空白而不是只判 {@code null}：{@code NOT NULL} 拦不住空串，而空税号在页面上就是一行空
     * 单元格，用户看到的是「数据在但没用」，那是最难被报上来的一类脏数据。
     *
     * @param value     字段值
     * @param label     字段的中文名，用于拼出可直接展示的错误信息
     * @param maxLength 列宽上限
     * @throws IllegalArgumentException 值为 {@code null} 或空白，或超出列宽
     */
    private static void requirePresent(String value, String label, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + "不能为空");
        }
        requireMaxLength(value, label, maxLength);
    }

    /**
     * 校验字段不超出列宽；{@code null} 视为未填，不算超长。
     *
     * @param value     字段值，可为 {@code null}
     * @param label     字段的中文名
     * @param maxLength 列宽上限
     * @throws IllegalArgumentException 值超出列宽
     */
    private static void requireMaxLength(String value, String label, int maxLength) {
        if (value != null && value.length() > maxLength) {
            throw new IllegalArgumentException(
                    label + "长度不能超过 " + maxLength + " 个字符，实际 " + value.length());
        }
    }
}
