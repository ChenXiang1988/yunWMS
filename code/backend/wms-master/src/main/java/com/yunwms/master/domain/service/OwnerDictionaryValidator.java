package com.yunwms.master.domain.service;

import com.yunwms.master.domain.model.NewOwner;
import com.yunwms.master.domain.port.DictLookup;

/**
 * 货主建档里「要查字典才能判定」的三条规则：客户类型、国家、州省归属。
 *
 * <p><b>为什么是领域服务而不是聚合上的方法：</b>{@code Owner} 与 {@code NewOwner} 只能回答自己
 * 手里的数据能回答的问题（非空、格式、跨字段搭配）。「这个客户类型是不是字典里的启用项」
 * 需要另一个数据源，聚合一旦持有字典查询能力，它就不再是纯内存对象，单测也要为它准备假字典。
 * 判定因此放在领域服务里：规则仍是业务规则，但它需要外部事实，由用例在执行时把事实传进来。
 *
 * <p><b>为什么不判「国家是否等于州省的上级」以外的地址格式：</b>格式（两位大写、美国必填州省）
 * 已经由 {@link com.yunwms.master.domain.model.OwnerAddress} 的构造器判过，本类不重复。
 * 两个类的分工是「聚合能自己回答的归聚合，要查字典的归这里」，因此同一件事不会有两处实现。
 *
 * <p>不依赖 Spring：本类由用例（3.1b）在构造时注入 {@link DictLookup}，不作为容器组件扫描。
 */
public class OwnerDictionaryValidator {

    /** 客户类型的字典类型编码，与 {@code V032__seed_dictionaries.sql} 的 {@code dict_type} 一致。 */
    private static final String OWNER_TYPE_DICT = "OWNER_TYPE";

    /** 国家的字典类型编码，同上。 */
    private static final String COUNTRY_DICT = "COUNTRY";

    private final DictLookup dictLookup;

    /**
     * @param dictLookup 字典查询端口，由 {@code wms-app} 装配到提供字典能力的模块上
     */
    public OwnerDictionaryValidator(DictLookup dictLookup) {
        this.dictLookup = dictLookup;
    }

    /**
     * 校验货主的字典取值。
     *
     * <p>三项都判完才返回，遇错即抛：一行里只报第一个错误，是为了让用户按提示逐项改完；
     * 一次性列出所有字段错误需要接口层的校验框架参与，那是 3.1b 的事，本类不预造那个结构。
     *
     * <p>空州省视为「未填」（非美国可选），不判归属：拿空串去问「属于哪个国家」只能得到否，
     * 而它在业务上并不是一个错误的取值。
     *
     * @param owner 待建档的货主，属性已通过聚合校验
     * @throws IllegalArgumentException 客户类型或国家不在字典的启用项中，或州省不属于所选国家
     */
    public void validate(NewOwner owner) {
        if (!dictLookup.isActiveItem(OWNER_TYPE_DICT, owner.ownerType())) {
            throw new IllegalArgumentException("客户类型不在字典 OWNER_TYPE 的启用项中：" + owner.ownerType());
        }
        String country = owner.address().country();
        if (!dictLookup.isActiveItem(COUNTRY_DICT, country)) {
            throw new IllegalArgumentException("注册国家不在国家字典的启用项中：" + country);
        }
        String province = owner.address().province();
        if (province != null && !province.isBlank() && !dictLookup.isProvinceOfCountry(province, country)) {
            throw new IllegalArgumentException("州省不属于所选国家：" + province + " 不属于 " + country);
        }
    }
}
