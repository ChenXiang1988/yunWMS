package com.yunwms.auth.interfaces.controller.request;

import com.yunwms.web.exception.BizException;

import java.util.ArrayList;
import java.util.List;

/**
 * 把请求体里以字符串传输的标识解析成 {@code Long}。
 *
 * <p>为什么需要它：{@code docs/api-convention.md} 第五节要求 JSON 里的标识一律是字符串
 * （超过 2^53 的 {@code Long} 在 JS 里会丢精度），而 application 与 domain 只认识 {@code Long}。
 * 字符串是 HTTP 协议事实，解析必须停在 interfaces 层，不能把「同一个标识的两种表示」带进用例。
 *
 * <p>为什么不各 Controller 自己写 {@code Long.parseLong}：解析口径（null 与空列表各自的含义、
 * 非法值映射到哪个错误码、提示里要不要带字段名）只能有一份定义，否则建号、分配角色、建角色、
 * 编辑角色四个入口会各自漂移；更要紧的是，{@code NumberFormatException} 漏到全局兜底会变成
 * 50000，把「调用方传错了」伪装成「服务端故障」，用户据此无法自查。
 *
 * <p>本类不是对外契约：它只服务于本模块 interfaces 层的协议转换，声明为 public 仅因为
 * 调用它的 Controller 在父包 {@code controller} 下、与本包不同包。
 */
public final class RequestIds {

    private RequestIds() {
    }

    /**
     * 解析可选的单个标识。
     *
     * @param raw       请求体里的原始字符串，可为 {@code null}
     * @param fieldName 字段名，用于拼装错误提示，使调用方能定位到具体字段
     * @return {@code raw} 为 {@code null} 时返回 {@code null}，否则返回其十进制数值
     * @throws BizException 值为空白或不是十进制整数时抛出，错误码 40000
     */
    public static Long optionalId(String raw, String fieldName) {
        if (raw == null) {
            return null;
        }
        return parse(raw, fieldName);
    }

    /**
     * 逐项解析可选的标识列表。
     *
     * <p>三种「空」不合并：{@code null} 表示字段被省略，空列表表示调用方明确提交了一组空集合，
     * 两者在 HTTP 上都能表示「没有标识」，但在整批替换语义（分配角色、编辑授权）里意图不同——
     * 省略更可能是没想清楚要提交什么，而它的后果是悄悄清空已有的关联。因此这里原样传递，
     * 由用例层按各自语义判定，解析器不做归一。
     *
     * @param raws      请求体里的原始字符串列表，可为 {@code null}
     * @param fieldName 字段名，用于拼装错误提示
     * @return {@code raws} 为 {@code null} 时返回 {@code null}；空列表返回空列表；
     *         否则按原顺序返回逐个解析出的数值
     * @throws BizException 任意元素为 {@code null}、空白或不是十进制整数时抛出，错误码 40000
     */
    public static List<Long> optionalIds(List<String> raws, String fieldName) {
        if (raws == null) {
            return null;
        }
        List<Long> ids = new ArrayList<>(raws.size());
        for (String raw : raws) {
            ids.add(parse(raw, fieldName));
        }
        return ids;
    }

    /**
     * 解析单个值。
     *
     * <p>先 trim 再判定：粘贴来的标识常带首尾空白，那是传输噪音而不是业务值；但 trim 之后为空
     * 说明调用方传了一个「看不见的值」，必须拒绝，不能当成 0 —— 0 是一个合法但通常不存在的标识，
     * 当成 0 会让一条错误的请求以「标识不存在」的形式返回，掩盖真正的原因。
     *
     * @param raw       原始字符串，可为 {@code null}（列表元素允许为 {@code null}）
     * @param fieldName 字段名，用于拼装错误提示
     * @return 解析出的十进制数值
     * @throws BizException 值为空白或不是十进制整数（含超出 {@code Long} 范围）时抛出，错误码 40000
     */
    private static Long parse(String raw, String fieldName) {
        String text = raw == null ? "" : raw.trim();
        if (text.isEmpty()) {
            throw BizException.badRequest(fieldName + " 不能为空，必须是字符串形式的十进制整数");
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException ex) {
            throw BizException.badRequest(fieldName + " 必须是字符串形式的十进制整数，实际为：" + text);
        }
    }
}
