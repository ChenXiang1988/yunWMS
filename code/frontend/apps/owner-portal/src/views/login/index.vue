<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { Button, Form, FormItem, Input, InputPassword } from 'ant-design-vue'
import { setToken } from '@yunwms/request'
import { notifySuccess } from '@/api/notify'

const router = useRouter()
const route = useRoute()
const { t } = useI18n()

const loading = ref(false)

const form = reactive({
  mobile: '',
  password: ''
})

const rules = {
  mobile: [{ required: true, message: '请输入手机号' }],
  password: [{ required: true, message: '请输入密码' }]
}

async function submit() {
  loading.value = true
  // 骨架阶段：后端尚未就绪，本地写入令牌以打通路由与布局
  window.setTimeout(() => {
    setToken('skeleton-token')
    loading.value = false
    notifySuccess('登录成功（骨架演示）')
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
        <FormItem :label="t('login.mobile')" name="mobile">
          <Input v-model:value="form.mobile" placeholder="13800000000" size="large" />
        </FormItem>
        <FormItem :label="t('login.password')" name="password">
          <InputPassword v-model:value="form.password" size="large" />
        </FormItem>
        <Button
          type="primary"
          size="large"
          html-type="submit"
          :loading="loading"
          block
          class="login__submit"
        >
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
  width: 380px;
  padding: var(--space-8);
  background: var(--color-bg-container);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
}

.login__title {
  margin: 0;
  font-size: var(--text-xl);
  line-height: var(--leading-xl);
  font-weight: var(--weight-semibold);
  color: var(--color-text-primary);
}

.login__subtitle {
  margin: var(--space-1) 0 var(--space-6);
  font-size: var(--font-size-base);
  color: var(--color-text-tertiary);
}

.login__submit {
  width: 100%;
  margin-top: var(--space-2);
}

.login__tip {
  margin: var(--space-5) 0 0;
  font-size: var(--text-xs);
  line-height: var(--leading-xs);
  color: var(--color-text-tertiary);
}
</style>
