package com.yunwms.auth.domain.model;

/**
 * 字典类型列表的一行：类型本身，加上它的项数。
 *
 * <p>{@code itemCount} 与类型行必须来自同一次查询：逐类再查一次项数会让类型列表的 SQL 条数
 * 随字典类型数量增长，而这张表是「所有模块共用」的，类型只会越来越多。
 *
 * <p>{@code itemCount} 计的是<b>未删除</b>的项，含已停用的项——它是管理员在字典管理页看到的
 * 「这一类有多少条」，不是「有多少条可选」。下拉要的是启用项，由项查询的筛选条件决定。
 *
 * <p>没有 {@code id}：字典类型在业务上只用 {@code dictType} 引用（{@code sys_dict_item.dict_type}
 * 也是用它做外键），把自增主键暴露出去只会诱使调用方按 id 传参。
 *
 * @param dictType  类型编码，如 {@code OWNER_TYPE}，业务代码与配置按它引用字典
 * @param name      中文名
 * @param nameEn    英文名，可为 {@code null}
 * @param itemCount 该项下未删除的字典项数量
 * @param status    类型状态；停用的类型不应再被新的引用处使用
 */
public record DictTypeSummary(String dictType,
                              String name,
                              String nameEn,
                              long itemCount,
                              DictStatus status) {
}
