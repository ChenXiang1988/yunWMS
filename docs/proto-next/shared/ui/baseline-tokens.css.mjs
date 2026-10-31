/**
 * 基线令牌层。
 *
 * 变量与取值全部来自 docs/specs/01-PC端设计规范.md 第 1 节，
 * 该节标明唯一权威来源是 docs/web 端原型模板_20261013.html 的 [FIXED-1] 块。
 * 变量名沿用基线的 --gray-* / --primary-* / --radius-* / --shadow-*，
 * 便于直接对照基线 CSS，不另造一套别名。
 *
 * 这里只声明变量，不写任何具体样式规则。
 */
export const BASELINE_TOKENS = `
:root{
  /* --- 品牌主色 --- */
  --primary-50:   #f3efff;
  --primary-100:  #ece4ff;
  --primary-200:  #d9caff;
  --primary-400:  #8a5cff;
  --primary-500:  #6424fe;
  --primary-600:  #6424fe;
  --primary-700:  #4f1bcc;

  /* --- 中性色 --- */
  --gray-0:   #ffffff;
  --gray-50:  #f5f5f5;
  --gray-100: #f1f2f4;
  --gray-200: #e5e5e5;
  --gray-300: #d1d5db;
  --gray-400: #8a8f98;
  --gray-500: #687386;
  --gray-600: #545b68;
  --gray-700: #414753;
  --gray-800: #30343b;
  --gray-900: #171717;

  /* --- 语义色 --- */
  --error-500:   #e5484d;
  --success-500: #1f9d65;
  --warning-500: #d97706;

  --warning-soft:  #fff7e6;
  --success-soft:  #eaf8f1;
  --danger-soft:   #fff0f0;
  --disabled-soft: #f1f2f4;

  --surface-sunken: #fafafa;
  --row-hover:      #fcfbff;
  --row-selected:   #f7f3ff;

  /* --- 布局尺寸 --- */
  --topbar-height: 54px;
  --sidebar-width: 188px;
  --sidebar-collapsed-width: 64px;

  /* --- 圆角：规范限定 {6, 6, 8, 999}px --- */
  --radius-sm: 6px;
  --radius-md: 6px;
  --radius-lg: 8px;
  --radius-pill: 999px;

  /* --- 阴影 --- */
  --shadow-sm:   0 1px 2px 0 rgb(0 0 0 / 0.05);
  --shadow-md:   0 4px 6px -1px rgb(0 0 0 / 0.1), 0 2px 4px -2px rgb(0 0 0 / 0.1);
  --shadow-soft: 0 1px 2px rgba(16, 12, 40, .04), 0 8px 22px rgba(74, 55, 150, .06);
  --shadow-lift: 0 12px 30px rgba(74, 55, 150, .13);
  --shadow-panel: 0 18px 48px rgb(0 0 0 / 14%);

  /* --- 动效 --- */
  --ease: cubic-bezier(.2, .7, .3, 1);
  --transition-fast:   150ms ease;
  --transition-normal: 220ms ease;
}
`;
