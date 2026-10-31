/**
 * APP 外壳：舞台 → 机身 → 屏幕 → 按键区。
 *
 * 尺寸取自归档中的真实实现：
 *   屏幕 375×720 · 机身圆角 30px · 按键区 92px · 状态栏 28px · 导航 48px · 操作栏 56px
 * 令牌为 APP 自有的一套（品牌绿 #018D71），与 PC 的 WDS 令牌分开。
 */

const esc = (v) => String(v ?? '')
  .replace(/&/g, '&amp;').replace(/</g, '&lt;')
  .replace(/>/g, '&gt;').replace(/"/g, '&quot;');

export function AppShell({ statusbar = '', navbar = '', content = '', actionbar = '', overlays = '' } = {}) {
  return `<div class="app-stage">
  <div class="app-device" role="application" aria-label="西之月 WMS APP 端">
    <div class="app-cam"></div>
    <span class="app-led"></span>
    <span class="app-key-side app-key-side--l"></span>
    <span class="app-key-side app-key-side--r"></span>

    <div class="app-screen">
      <div class="app-statusbar">${statusbar}</div>
      <header class="app-navbar">${navbar}</header>
      <main class="app-content">${content}</main>
      <footer class="app-actionbar">${actionbar}</footer>
      <div class="app-overlays">
        <div class="app-mask"></div>
        <div class="app-sheet"></div>
        <div class="app-modal"></div>
        <div class="app-toast"></div>
      </div>
    </div>

    <div class="app-keypad">
      <button class="app-key" data-key="back">返回</button>
      <button class="app-key" data-key="home">主页</button>
      <button class="app-key" data-key="menu">菜单</button>
      <button class="app-key" data-key="clear">清空</button>
      <button class="app-key app-key--scan" data-key="scan">扫描</button>
      <button class="app-key app-key--ok" data-key="ok">确定</button>
    </div>
  </div>
</div>`;
}

/* 导航栏：返回 + 标题 + 右侧槽 */
export function AppNavbar({ title = '', showBack = false, right = '' } = {}) {
  return `${showBack ? '<button class="app-nav-back" data-key="back" aria-label="返回">‹</button>' : ''}
    <div class="app-nav-title">${esc(title)}</div>
    <div class="app-nav-right">${right}</div>`;
}

/* 状态栏：作业员 + 设备 + 时钟 */
export function AppStatusbar({ operator = '', device = '', time = '' } = {}) {
  return `<span class="app-status-left">${esc(operator || '未登录')}</span>
    <span class="app-status-mid">${esc(device)}</span>
    <span class="app-status-right">${esc(time)}</span>`;
}

export const APP_CSS = `
:root{
  --pda-color-brand:#018D71;
  --pda-color-action-primary:#007D64;
  --pda-color-brand-soft:#E6F4EF;
  --pda-color-page:#F2F2F2;
  --pda-color-surface:#FFFFFF;
  --pda-color-text-primary:#333333;
  --pda-color-text-secondary:#666666;
  --pda-color-text-muted:#999999;
  --pda-color-text-inverse:#FFFFFF;
  --pda-color-divider:#F2F2F2;
  --pda-color-border:#DDDDDD;
  --pda-color-danger:#DD524D;
  --pda-color-danger-soft:#FEF3F2;
  --pda-color-warning-soft:#FFF7ED;
  --pda-color-warning-text:#B45309;
  --pda-color-mask:rgba(0,0,0,.40);
  --pda-font-family:system-ui,-apple-system,"PingFang SC","Microsoft YaHei",sans-serif;
  --pda-font-caption:12px;
  --pda-font-body:14px;
  --pda-space-micro:4px;
  --pda-space-small:8px;
  --pda-space-content:12px;
  --pda-space-page:16px;
  --pda-space-large:24px;
  --pda-device-screen-w:375px;
  --pda-device-screen-h:720px;
  --pda-device-shell:#2b2f33;
  --pda-device-edge:#3a3f45;
  --pda-device-radius:30px;
  --pda-device-keypad-h:92px;
  --pda-device-scan-key:#f5b301;
  --pda-layout-action-bar-min-height:56px;
}

*{box-sizing:border-box}
body{
  margin:0;font-family:var(--pda-font-family);font-size:var(--pda-font-body);
  color:var(--pda-color-text-primary);background:#e9ecef;
}

/* 舞台：居中，可滚动 */
/* 舞台：默认隐藏，由路由显示当前一屏。
   display:flex 会压过浏览器默认的 [hidden]{display:none}，故必须显式声明。 */
.app-stage{display:none}
.app-stage:not([hidden]){
  min-height:100vh;display:flex;
  /* 内容高于视口时从顶部开始，避免 margin:auto 把机身推到视口外 */
  align-items:flex-start;justify-content:center;
  padding:var(--pda-space-large) var(--pda-space-page);
  overflow:auto;
  background:var(--pda-color-page);
}

/* 机身 */
.app-device{
  position:relative;margin:0 auto;
  width:calc(var(--pda-device-screen-w) + 30px);
  flex:0 0 auto;
  padding:14px 15px 15px;
  border-radius:var(--pda-device-radius);
  background:linear-gradient(180deg,#34393e 0%,var(--pda-device-shell) 40%);
  box-shadow:0 18px 46px rgba(16,24,40,.28), inset 0 1px 0 rgba(255,255,255,.08);
}
.app-cam{
  position:absolute;top:7px;left:50%;transform:translateX(-50%);
  width:44px;height:4px;border-radius:2px;background:var(--pda-device-shell);
}
.app-led{
  position:absolute;top:8px;right:18px;width:6px;height:6px;border-radius:50%;
  background:#2ecc71;box-shadow:0 0 6px rgba(46,204,113,.8);
}
.app-key-side{position:absolute;right:-3px;width:3px;border-radius:2px;background:var(--pda-device-edge)}
.app-key-side--l{left:-3px;right:auto;height:52px;top:150px}
.app-key-side--r{height:74px;top:190px}

/* 屏幕 */
.app-screen{
  position:relative;width:var(--pda-device-screen-w);height:var(--pda-device-screen-h);
  display:flex;flex-direction:column;overflow:hidden;
  border-radius:8px;background:var(--pda-color-page);
}

/* 状态栏 28px */
.app-statusbar{
  flex:0 0 auto;height:28px;display:flex;align-items:center;gap:var(--pda-space-small);
  padding:0 var(--pda-space-content);
  background:#12312b;color:#cfe9e2;font-size:var(--pda-font-caption);
}
.app-status-left{font-weight:500}
.app-status-mid{flex:1;text-align:center;opacity:.8}
.app-status-right{opacity:.8}

/* 导航 48px */
.app-navbar{
  flex:0 0 auto;min-height:48px;display:flex;align-items:center;gap:var(--pda-space-small);
  padding:0 var(--pda-space-content);
  background:var(--pda-color-surface);
  border-bottom:1px solid var(--pda-color-divider);
}
.app-nav-back{
  width:28px;height:28px;flex:none;display:grid;place-items:center;
  border:0;border-radius:50%;background:transparent;
  color:var(--pda-color-text-primary);font-size:18px;line-height:1;cursor:pointer;
}
.app-nav-title{flex:1;min-width:0;font-size:15px;font-weight:600;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.app-nav-right{flex:none;display:flex;align-items:center;gap:var(--pda-space-small);font-size:var(--pda-font-caption);color:var(--pda-color-text-secondary)}

/* 内容：唯一滚动容器 */
.app-content{
  flex:1;min-height:0;overflow-y:auto;overflow-x:hidden;
  padding-bottom:var(--pda-space-page);-webkit-overflow-scrolling:touch;
}

/* 操作栏 */
.app-actionbar{
  flex:0 0 auto;min-height:var(--pda-layout-action-bar-min-height);
  display:flex;align-items:center;gap:var(--pda-space-small);
  padding:var(--pda-space-small) var(--pda-space-content);
  background:var(--pda-color-surface);
  border-top:1px solid var(--pda-color-divider);
}

/* 弹层 */
.app-overlays{position:absolute;inset:0;pointer-events:none;z-index:1000}
.app-mask{position:absolute;inset:0;background:var(--pda-color-mask);opacity:0;pointer-events:none;transition:opacity .18s}
.app-sheet,.app-modal{position:absolute;left:0;right:0;background:var(--pda-color-surface);transform:translateY(100%);transition:transform .22s}
.app-sheet{bottom:0;max-height:70vh;border-radius:12px 12px 0 0;z-index:1010}
.app-modal{top:50%;bottom:auto;left:16px;right:16px;border-radius:12px;transform:translateY(-40%) scale(.96);opacity:0;z-index:1010}
.app-toast{
  position:absolute;left:50%;bottom:96px;transform:translateX(-50%) translateY(8px);
  max-width:80%;padding:8px 14px;border-radius:8px;
  background:rgba(0,0,0,.78);color:#fff;font-size:var(--pda-font-caption);
  opacity:0;pointer-events:none;transition:opacity .18s,transform .18s;z-index:1100;
}
.app-overlays.is-active{pointer-events:auto}
.app-overlays.is-masked .app-mask{opacity:1;pointer-events:auto}
.app-overlays.is-sheet .app-sheet{transform:translateY(0)}
.app-overlays.is-modal .app-modal{transform:translateY(-50%) scale(1);opacity:1}
.app-toast.is-show{opacity:1;transform:translateX(-50%) translateY(0)}

/* 按键区 */
.app-keypad{
  display:grid;grid-template-columns:repeat(4,1fr);grid-template-rows:repeat(2,1fr);
  gap:6px;height:var(--pda-device-keypad-h);
  margin-top:12px;padding:8px;border-radius:12px;background:var(--pda-device-shell);
}
.app-key{
  border:0;border-radius:8px;cursor:pointer;
  background:#2f353b;color:#d7dee5;
  font-family:inherit;font-size:12px;font-weight:500;
  box-shadow:inset 0 -2px 0 rgba(0,0,0,.25);
}
.app-key:active{transform:translateY(1px);box-shadow:none}
.app-key--scan{background:var(--pda-device-scan-key);color:#3a2c00;font-weight:700}
.app-key--ok{background:#2e7d63;color:#fff;font-weight:700}

/* 窄屏：机身等比收窄，保持 375 设计基准不变形 */
@media (max-width: 430px){
  .app-stage:not([hidden]){padding:12px}
  .app-device{
    width:100%;max-width:calc(var(--pda-device-screen-w) + 30px);
    padding:10px;
  }
  .app-screen{width:100%;height:auto;min-height:calc(var(--pda-device-screen-h) - 40px)}
  .app-keypad{height:80px}
}
`;
