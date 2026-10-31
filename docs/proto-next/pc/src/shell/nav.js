(function(){
  var PAGE_LOC = __PAGE_LOC__;
  var SECTIONS = __SECTIONS__;
  var DEFAULT_PAGE = __DEFAULT_PAGE__;

  var sidebar=document.getElementById('sidebar');
  var topBtns=[].slice.call(document.querySelectorAll('[data-nav]'));
  var secBtns=[].slice.call(document.querySelectorAll('[data-nav-sec]'));
  var pages=[].slice.call(document.querySelectorAll('[data-page]'));
  var tabsRow=document.querySelector('.tabs-row');
  var titleEl=document.getElementById('pageTitle');
  var titleBar=document.getElementById('pageTitleBar');
  var emptyEl=document.getElementById('emptyState');
  var contentEl=document.getElementById('pageContent');
  var toastWrap=document.getElementById('toastWrap');

  var state={ top:'', sec:'', page:'' };

  /* ---- toast（原型提示，不发起请求） ---- */
  function toast(msg){
    if(!toastWrap) return;
    var el=document.createElement('div');
    el.className='toast'; el.textContent=msg;
    toastWrap.appendChild(el);
    setTimeout(function(){ el.classList.add('is-out'); }, 2200);
    setTimeout(function(){ if(el.parentNode) el.parentNode.removeChild(el); }, 2600);
  }

  /* ---- 混合路由：优先写 hash（可刷新、可后退、URL 可分享），
         写入未生效时（如编辑器预览的沙箱 iframe）降级为直接渲染 ---- */
  function go(key){
    if(!key) return;
    try { location.hash = key; } catch(e){}
    if (location.hash !== '#' + key) { render(key); }
  }

  function pageExists(key){
    return pages.some(function(p){ return p.getAttribute('data-page')===key; });
  }
  function labelOf(el){ return el ? el.textContent.trim() : ''; }

  function setSidebarActive(){
    topBtns.forEach(function(b){
      b.classList.toggle('current', b.getAttribute('data-nav')===state.top);
    });
    secBtns.forEach(function(b){
      b.classList.toggle('current', b.getAttribute('data-nav-sec')===state.sec);
    });
  }

  /* 页头三级页签：数据源是当前二级项的页签列表，不是侧栏的复刻 */
  function renderTabs(){
    if(!tabsRow) return;
    var list=SECTIONS[state.sec]||[];
    if(!list.length){ tabsRow.innerHTML=''; return; }
    tabsRow.innerHTML=list.map(function(t){
      /* 未实现的页面置灰不可点，避免点开整页空态 */
      var off=t.built===false;
      return '<button type="button" class="tab'+(t.key===state.page?' active':'')+
             (off?' is-off':'')+'" data-ctx="'+t.key+'"'+
             (off?' disabled':'')+'>'+t.name+'</button>';
    }).join('');
  }

  /* 页标题：与当前三级页签同名时隐藏，避免重复（与旧版一致） */
  function renderTitle(name){
    if(!titleEl||!titleBar) return;
    titleEl.textContent=name||'';
    var tabLabel=(SECTIONS[state.sec]||[]).filter(function(t){return t.key===state.page;})[0];
    var same = !!(tabLabel && name && tabLabel.name===name);
    titleBar.classList.toggle('is-title-hidden', same);
  }

  function render(key){
    var loc=PAGE_LOC[key];
    if(!loc){ key=DEFAULT_PAGE; loc=PAGE_LOC[key]; }
    if(!loc) return;
    state.top=loc.top; state.sec=loc.sec; state.page=key;

    var hit=null;
    pages.forEach(function(p){
      if(p.getAttribute('data-page')===key){ p.removeAttribute('hidden'); hit=p; }
      else p.setAttribute('hidden','');
    });

    /* 未纳入本原型的页面：整页空态 */
    var isEmpty = !hit;
    if(emptyEl) emptyEl.hidden = !isEmpty;
    if(contentEl){ contentEl.hidden = isEmpty; }
    var tab=(SECTIONS[state.sec]||[]).filter(function(t){return t.key===state.page;})[0]||{};
    renderTitle(tab.name||'');

    /* 所在分组保持展开 */
    topBtns.forEach(function(b){
      var box=b.nextElementSibling;
      if(box && box.classList.contains('nav-children') && b.getAttribute('data-nav')===state.top){
        box.removeAttribute('hidden');
        b.setAttribute('aria-expanded','true');
      }
    });

    setSidebarActive();
    renderTabs();
  }

  /* 一级分组：纯手风琴展开 / 收起，不改变当前页面。
     收起态下点一级先展开侧栏，再展开该组。 */
  topBtns.forEach(function(b){
    b.addEventListener('click',function(e){
      e.preventDefault();
      if(sidebar && sidebar.classList.contains('collapsed')){
        sidebar.classList.remove('collapsed');
      }
      var box=b.nextElementSibling;
      if(!box || !box.classList.contains('nav-children')) return;
      var open=!box.hasAttribute('hidden');
      /* 当前组始终展开，避免收起后丢失定位 */
      if(open && b.getAttribute('data-nav')!==state.top){
        box.setAttribute('hidden','');
        b.setAttribute('aria-expanded','false');
      }else{
        box.removeAttribute('hidden');
        b.setAttribute('aria-expanded','true');
      }
    });
  });

  /* 二级项：进入该段，切到它的第一个三级页 */
  secBtns.forEach(function(b){
    b.addEventListener('click',function(e){
      e.preventDefault();
      var sec=b.getAttribute('data-nav-sec');
      var list=SECTIONS[sec]||[];
      if(!list.length) return;
      /* 落在该段第一个可点的页面。整段都未实现时不换页。 */
      var done=null;
      for(var i=0;i<list.length;i++){
        if(list[i].built!==false && pageExists(list[i].key)){ done=list[i]; break; }
      }
      if(!done) return;
      go(done.key);
      if(!pageExists(done.key)) render(done.key);
    });
  });

  /* 三级页签：唯一的换页入口 */
  document.addEventListener('click',function(e){
    var tab=e.target.closest && e.target.closest('[data-ctx]');
    if(!tab) return;
    /* 置灰项：不换页，也不进 hash */
    if(tab.disabled || (tab.className||'').indexOf('is-off')>=0) return;
    e.preventDefault();
    go(tab.getAttribute('data-ctx'));
  });

  /* 侧栏折叠 */
  var collapseBtn=document.getElementById('collapseBtn');
  if(collapseBtn&&sidebar){
    collapseBtn.addEventListener('click',function(){
      var folded=!sidebar.classList.contains('collapsed');
      sidebar.classList.toggle('collapsed',folded);
      collapseBtn.setAttribute('aria-label',folded?'展开侧栏':'收起侧栏');
    });
  }
  var helpBtn=document.getElementById('helpBtn');
  if(helpBtn){ helpBtn.addEventListener('click',function(){ toast('帮助中心（原型示意）'); }); }

  /* 顶栏工具按钮与店铺切换 */
  document.addEventListener('click',function(e){
    var t=e.target.closest && e.target.closest('[data-toast]');
    if(t){ toast(t.getAttribute('data-toast')); return; }
    var trg=e.target.closest && e.target.closest('#shopSwitcherTrigger');
    var menu=document.getElementById('shopSwitcherMenu');
    if(trg&&menu){
      menu.classList.toggle('open');
      if(!menu.innerHTML){
        menu.innerHTML=['官方集采自营店','官方工厂店','平台工业品店','POP 店','平台运营'].map(function(s){
          return '<button type="button" class="dropdown-item" data-shop="'+s+'">'+s+'</button>';
        }).join('');
      }
      return;
    }
    var shop=e.target.closest && e.target.closest('[data-shop]');
    if(shop){
      var lab=document.getElementById('shopSwitcherLabel');
      if(lab) lab.textContent=shop.getAttribute('data-shop');
      if(menu) menu.classList.remove('open');
      toast('已切换：'+shop.getAttribute('data-shop'));
    }
  });

  /* 全局搜索：清空按钮显隐 */
  var gs=document.getElementById('globalSearch');
  var gsc=document.getElementById('globalSearchClear');
  if(gs){
    var box=gs.parentNode;
    gs.addEventListener('input',function(){
      if(box) box.classList.toggle('has-value', !!gs.value);
    });
    if(gsc) gsc.addEventListener('click',function(){ gs.value=''; box.classList.remove('has-value'); });
  }

  window.addEventListener('hashchange',function(){
    var want=(location.hash||'').replace('#','');
    if(want) render(want);
  });

  render((location.hash||'').replace('#','') || DEFAULT_PAGE);
})();
