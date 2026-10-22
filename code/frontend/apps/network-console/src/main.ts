import { createApp } from 'vue'
import 'ant-design-vue/dist/reset.css'
import '@yunwms/tokens'
import '@/styles/index.scss'
import App from './App.vue'
import { router } from './router'
import { i18n } from './locales'

createApp(App).use(router).use(i18n).mount('#app')
