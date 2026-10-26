package com.yunwms.master.domain.port;

import java.util.OptionalLong;

/**
 * 当前请求的操作人：本模块需要写 {@code created_by} / {@code updated_by} 时唯一的取人入口。
 *
 * <p><b>为什么本模块自己声明，而不是复用 {@code wms-auth} 的会话端口：</b>架构约束是
 * {@code auth} 与 {@code master} 互不引用（{@code docs/coding-project.md} 第二节，由
 * {@code ModuleBoundaryTest} 拦截），取不到对方的类型。
 *
 * <p><b>为什么不像 {@link OwnerCodeGenerator} 与 {@link DictLookup} 那样由 {@code wms-app} 装配适配器：</b>
 * 那两个端口封装的是 {@code wms-auth} 的<b>业务能力</b>（编码规则、字典数据），只有 wms-app 同时认识两边，
 * 适配器只能放在那里。而「当前登录的是谁」不是 auth 的业务能力，是 Sa-Token 这个技术组件的事实——
 * 本模块的接口层为了 {@code @SaCheckPermission} 本来就依赖它。为一个无业务语义的登录标识再穿一层
 * 跨模块适配器，只是把同一行库调用搬到第三个模块。
 *
 * <p><b>为什么只有 {@code currentUserId} 一个方法：</b>端口按使用方的需要声明。{@code wms-auth} 的会话端口
 * 另外三个方法是「需先改密」标记的读写，那是登录链路的内部状态，本模块消费不到，复制过来只会多一处
 * 「本模块也能改会话」的错觉。
 *
 * <p>用户标识只暴露 {@link OptionalLong}，不抛会话实现自己的异常：端口不认识具体会话库，
 * 「当前没有登录」由调用方翻译成业务错误码。
 */
public interface CurrentOperator {

    /**
     * 取当前请求的操作人标识。
     *
     * @return 登录用户标识；当前请求没有有效会话时为空
     */
    OptionalLong currentUserId();
}
