import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'
import '@yunwms/tokens'
import '@yunwms/tokens/element-plus'
import '@/styles/index.scss'
import App from './App.vue'
import { router } from './router'
import { i18n } from './locales'

createApp(App).use(ElementPlus, { locale: zhCn }).use(router).use(i18n).mount('#app')
