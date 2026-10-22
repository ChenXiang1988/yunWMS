/**
 * 设计令牌唯一真源。
 *
 * 取值约定：
 * - 尺寸存数字（14 而非 '14px'），因为 antd 的 fontSize / borderRadius 要数字；
 *   生成 CSS 时由 scripts/emit.ts 补 px
 * - 颜色、阴影、缓动存字符串，原样输出
 * - 本文件是仓库内唯一允许出现色值字面量的位置（另一处是生成产物）
 */
export const primitives = {
  color: {
    blue: {
      '50': '#eef4ff',
      '100': '#dbe6ff',
      '200': '#b9ceff',
      '300': '#8aabff',
      '400': '#5a86f5',
      '500': '#2b63e0',
      '600': '#1f4fbd',
      '700': '#183e96'
    },
    gray: {
      '0': '#ffffff',
      '25': '#fcfcfd',
      '50': '#f7f8fa',
      '100': '#f1f3f5',
      '200': '#e4e7eb',
      '300': '#d0d5dc',
      '400': '#a8b0bb',
      '500': '#7c8593',
      '600': '#5a6472',
      '700': '#3e4652',
      '800': '#262c35',
      '900': '#14181d'
    },
    green: {
      '50': '#e7f6ee',
      '100': '#d5ecdf',
      '200': '#c2e4d3',
      '300': '#8cc9aa',
      '400': '#4fae80',
      '500': '#12925a',
      '600': '#0e7548'
    },
    orange: {
      '50': '#fff4e5',
      '100': '#f7e3cf',
      '200': '#f4d8bc',
      '300': '#edbd86',
      '400': '#e49c4c',
      '500': '#d97706',
      '600': '#b45f05'
    },
    red: {
      '50': '#fdeeee',
      '100': '#f6dcd9',
      '200': '#f3cec9',
      '300': '#eca19a',
      '400': '#e46a60',
      '500': '#d92d20',
      '600': '#b42318'
    },
    cyan: {
      '50': '#e6f5f9',
      '100': '#d2e8ed',
      '200': '#bfdde5',
      '300': '#86c0ce',
      '400': '#4d9db2',
      '500': '#0e7490',
      '600': '#0b5c72'
    }
  },
  font: {
    family: {
      sans: '"Inter", -apple-system, BlinkMacSystemFont, "PingFang SC", "Hiragino Sans GB", "Microsoft YaHei", "Noto Sans SC", sans-serif',
      mono: '"JetBrains Mono", "SFMono-Regular", Menlo, Consolas, monospace'
    },
    size: {
      '2xs': 11,
      xs: 12,
      sm: 13,
      base: 14,
      md: 16,
      lg: 18,
      xl: 20,
      '2xl': 24
    },
    leading: {
      '2xs': 16,
      xs: 18,
      sm: 20,
      base: 22,
      md: 24,
      lg: 26,
      xl: 28,
      '2xl': 32
    },
    weight: {
      regular: 400,
      medium: 500,
      semibold: 600
    }
  },
  space: {
    '0': 0,
    '1': 4,
    '2': 8,
    '3': 12,
    '4': 16,
    '5': 20,
    '6': 24,
    '8': 32,
    '10': 40,
    '12': 48,
    '16': 64
  },
  radius: {
    sm: 2,
    md: 6,
    lg: 8,
    full: 999
  },
  shadow: {
    xs: '0 1px 2px 0 rgba(20, 24, 29, 0.06)',
    sm: '0 1px 3px 0 rgba(20, 24, 29, 0.08), 0 1px 2px -1px rgba(20, 24, 29, 0.06)',
    md: '0 4px 12px -2px rgba(20, 24, 29, 0.1), 0 2px 6px -2px rgba(20, 24, 29, 0.06)',
    lg: '0 12px 32px -4px rgba(20, 24, 29, 0.14)',
    popup: '0 6px 16px -4px rgba(20, 24, 29, 0.16)'
  },
  overlay: {
    mask: 'rgba(20, 24, 29, 0.45)'
  },
  z: {
    dropdown: 1000,
    sticky: 1020,
    fixed: 1030,
    modalBackdrop: 1040,
    modal: 1050,
    popover: 1060,
    message: 1070
  },
  motion: {
    durationFast: '120ms',
    durationBase: '200ms',
    durationSlow: '300ms',
    easeOut: 'cubic-bezier(0.16, 1, 0.3, 1)',
    easeInOut: 'cubic-bezier(0.4, 0, 0.2, 1)'
  }
} as const
