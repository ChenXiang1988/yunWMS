<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { Button, Form, FormItem, Input, InputPassword } from 'ant-design-vue'
import { login } from '@/api/auth'
import { resolveFailureMessage } from '@/api/errors'
import AuthCard from '@/components/auth/AuthCard.vue'
import { PORTAL } from '@/config/portal'
import { session } from '@/stores/session'

const router = useRouter()
const route = useRoute()
const { t } = useI18n()

const loading = ref(false)
/** 行内错误：40101 / 40102 / 40103 三种文案长度接近，行内不遮挡表单，用户能立刻重试 */
const failure = ref('')

const form = reactive({
  account: '',
  password: ''
})

const rules = {
  account: [{ required: true, message: '请输入账号' }],
  password: [{ required: true, message: '请输入密码' }]
}

async function submit() {
  if (loading.value) {
    return
  }
  failure.value = ''
  loading.value = true
  try {
    const result = await login({ loginName: form.account, password: form.password })
    // 端校验放在保存令牌之前：进不了仓网端的账号不该在本应用留下任何会话痕迹。
    // 输入框内容保持原样，用户换个账号就能直接重试。
    if (!result.accessiblePortals.includes(PORTAL)) {
      failure.value = t('login.portalDenied')
      return
    }
    session.save(result)
    if (result.mustChangePassword) {
      void router.replace({ name: 'change-password' })
      return
    }
    void router.replace(readRedirect())
  } catch (error) {
    failure.value = resolveFailureMessage(error, t('login.failed'))
  } finally {
    loading.value = false
  }
}

/** 守卫带来的回跳地址；非字符串（数组或缺失）一律回工作台 */
function readRedirect(): string {
  const { redirect } = route.query
  return typeof redirect === 'string' && redirect !== '' ? redirect : '/dashboard'
}
</script>

<template>
  <AuthCard :title="t('login.title')" :subtitle="`${t('app.name')} · ${t('app.subtitle')}`">
    <Form :model="form" :rules="rules" layout="vertical" @finish="submit">
      <FormItem :label="t('login.account')" name="account">
        <Input
          v-model:value="form.account"
          size="large"
          autocomplete="username"
          :disabled="loading"
        />
      </FormItem>
      <FormItem :label="t('login.password')" name="password">
        <InputPassword
          v-model:value="form.password"
          size="large"
          autocomplete="current-password"
          :disabled="loading"
        />
      </FormItem>

      <p v-if="failure" class="login__failure" role="alert">{{ failure }}</p>

      <Button type="primary" size="large" html-type="submit" block :loading="loading">
        {{ t('login.submit') }}
      </Button>
    </Form>

    <p class="login__tip">{{ t('login.tip') }}</p>
  </AuthCard>
</template>

<style scoped>
.login__failure {
  margin: 0 0 var(--space-3);
  font-size: var(--text-xs);
  line-height: var(--leading-xs);
  color: var(--color-danger);
}

.login__tip {
  margin: var(--space-4) 0 0;
  font-size: var(--text-2xs);
  line-height: var(--leading-2xs);
  color: var(--color-text-tertiary);
}
</style>
