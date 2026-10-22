<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { Button, Form, FormItem, Input, InputPassword } from 'ant-design-vue'
import { setToken } from '@yunwms/request'

const router = useRouter()
const route = useRoute()
const { t } = useI18n()

const loading = ref(false)

const form = reactive({
  account: '',
  password: ''
})

const rules = {
  account: [{ required: true, message: '请输入账号' }],
  password: [{ required: true, message: '请输入密码' }]
}

/**
 * 登录成功的提示已移除：本应用的 message 实例注入在任务 3.1 落地（移除 EP 时一并接入），
 * 现阶段用静态 message 不会跟随令牌主题，故先不给提示——本页提交后立即跳转，提示价值也低。
 */
async function submit() {
  loading.value = true
  // 骨架阶段：后端尚未就绪，本地写入令牌以打通路由与布局
  window.setTimeout(() => {
    setToken('skeleton-token')
    loading.value = false
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/dashboard'
    void router.replace(redirect)
  }, 300)
}
</script>

<template>
  <div class="login">
    <div class="login__panel">
      <h1 class="login__title">{{ t('login.title') }}</h1>
      <p class="login__subtitle">{{ t('app.name') }} · {{ t('app.subtitle') }}</p>

      <Form :model="form" :rules="rules" layout="vertical" @finish="submit">
        <FormItem :label="t('login.account')" name="account">
          <Input v-model:value="form.account" size="large" />
        </FormItem>
        <FormItem :label="t('login.password')" name="password">
          <InputPassword v-model:value="form.password" size="large" />
        </FormItem>
        <Button type="primary" size="large" html-type="submit" :loading="loading" block>
          {{ t('login.submit') }}
        </Button>
      </Form>

      <p class="login__tip">{{ t('login.tip') }}</p>
    </div>
  </div>
</template>

<style scoped>
.login {
  display: grid;
  place-items: center;
  min-height: 100vh;
  background: var(--color-bg-page);
}

.login__panel {
  width: 360px;
  padding: var(--space-6);
  background: var(--color-bg-container);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
}

.login__title {
  margin: 0;
  font-size: var(--text-lg);
  line-height: var(--leading-lg);
  font-weight: var(--weight-semibold);
  color: var(--color-text-primary);
}

.login__subtitle {
  margin: var(--space-1) 0 var(--space-5);
  font-size: var(--font-size-base);
  color: var(--color-text-tertiary);
}

.login__tip {
  margin: var(--space-4) 0 0;
  font-size: var(--text-2xs);
  line-height: var(--leading-2xs);
  color: var(--color-text-tertiary);
}
</style>
