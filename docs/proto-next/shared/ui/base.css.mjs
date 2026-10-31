/**
 * 两端共用的基础样式。
 *
 * 全部取自 tokens.css 的 --wds-* 语义令牌，不出现硬编码色值。
 * 改视觉只改 tokens.css，不改这里。
 */

export const BASE_CSS = `
*{box-sizing:border-box}
body{
  margin:0;
  font-family:var(--wds-font-family, system-ui, -apple-system, "Segoe UI", "PingFang SC", sans-serif);
  font-size:var(--wds-size-font-size);
  line-height:1.6;
  color:var(--wds-color-text);
  background:var(--wds-color-bg-layout);
}

h1,h2,h3{margin:0;font-weight:500}
h1{font-size:var(--wds-size-font-size-xl);line-height:1.4}
h2{font-size:var(--wds-size-font-size-lg)}
small{font-size:var(--wds-size-font-xs)}

/* 页面容器：间距唯一由面板内边距控制 */
.page{
  padding:var(--wds-panel-padding);
  display:flex;
  flex-direction:column;
  gap:var(--wds-layout-gap-lg);
}

/* 页头 */
.page-head{display:flex;align-items:baseline;gap:var(--wds-layout-gap)}
.page-head .meta{
  color:var(--wds-color-text-secondary);
  font-size:var(--wds-size-font-xs);
}

/* 指标卡：同级模块统一用 layout-gap */
.stat-row{
  display:grid;
  grid-template-columns:repeat(auto-fit,minmax(120px,1fr));
  gap:var(--wds-layout-gap);
}
.stat{
  background:var(--wds-color-bg-container);
  border-radius:var(--wds-size-radius-md);
  padding:var(--wds-card-padding);
  box-shadow:var(--wds-size-shadow-xs);
}
.stat span{
  display:block;
  color:var(--wds-color-text-secondary);
  font-size:var(--wds-size-font-xs);
}
.stat b{
  display:block;
  margin-top:2px;
  font-size:var(--wds-size-font-size-xl);
  font-variant-numeric:tabular-nums;
  line-height:1.3;
}

/* 表格 */
.table-wrap{
  background:var(--wds-color-bg-container);
  border-radius:var(--wds-size-radius-md);
  overflow:auto;
  box-shadow:var(--wds-size-shadow-xs);
}
table{border-collapse:collapse;width:100%}
th,td{
  padding:var(--wds-table-cell-padding) var(--wds-layout-gap-lg);
  text-align:left;
  border-bottom:1px solid var(--wds-component-table-border, var(--wds-color-border-secondary));
  white-space:nowrap;
}
th{
  background:var(--wds-component-table-header-bg, var(--wds-color-background-subtle));
  font-weight:500;
  color:var(--wds-color-text-secondary);
  position:sticky;top:0;z-index:1;
}
tbody tr:hover{background:var(--wds-color-background-hover)}
tbody tr:last-child td{border-bottom:0}
.mono{font-family:ui-monospace,Menlo,monospace;font-size:var(--wds-size-font-xs)}
.num{text-align:right;font-variant-numeric:tabular-nums}

/* 状态标签 */
.tag{
  display:inline-block;
  padding:0 7px;
  border-radius:var(--wds-size-radius-sm);
  font-size:var(--wds-size-font-xs);
  line-height:var(--wds-control-height);
  height:calc(var(--wds-control-height) - 4px);
}
.tag.ok{background:var(--wds-color-success-bg);color:var(--wds-color-success-text)}
.tag.low{background:var(--wds-color-warning-bg);color:var(--wds-color-warning-text)}
.tag.out{background:var(--wds-color-error-bg);color:var(--wds-color-error-text)}
`;
