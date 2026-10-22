/**
 * Web 端 antd 主题。
 * 值全部来自 primitives，本文件不得出现色值字面量。
 *
 * 为什么没有 `ThemeConfig` 类型注解：
 * `packages/tokens` 不得依赖任何 UI 库（含类型导入，因为那同样要求安装该包）。
 * 结构正确性由调用点校验——ConfigProvider 的 theme 属性会做结构检查。
 *
 * 注意：antd 的 token 是 JS 对象，无法随 data-density 变化。
 * 本文件对应 comfortable 档；network-console 迁移时需要另一套 compact 值，
 * 切换方式是替换 ConfigProvider 的 theme，代价是一次全量样式重算。
 */
import { primitives } from './primitives'

export const antdTheme = {
  token: {
    /* 品牌与功能色 */
    colorPrimary: primitives.color.blue[500],
    colorSuccess: primitives.color.green[500],
    colorWarning: primitives.color.orange[500],
    colorError: primitives.color.red[500],
    colorInfo: primitives.color.cyan[500],

    /* 文字 */
    colorText: primitives.color.gray[900],
    colorTextSecondary: primitives.color.gray[600],
    colorTextTertiary: primitives.color.gray[500],
    colorTextDisabled: primitives.color.gray[400],

    /* 背景 */
    colorBgLayout: primitives.color.gray[50],
    colorBgContainer: primitives.color.gray[0],
    colorBgElevated: primitives.color.gray[0],

    /* 边框 */
    colorBorder: primitives.color.gray[200],
    colorBorderSecondary: primitives.color.gray[100],

    /* 字体与圆角 */
    fontFamily: primitives.font.family.sans,
    fontSize: primitives.font.size.base,
    borderRadius: primitives.radius.md
  }
}
