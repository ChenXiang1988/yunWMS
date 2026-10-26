<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { Button, Form, FormItem, InputPassword } from 'ant-design-vue'
import { changePassword } from '@/api/auth'
import { resolveFailureMessage } from '@/api/errors'
import { notifySuccess } from '@/api/notify'
import AuthCard from '@/components/auth/AuthCard.vue'
import { session } from '@/stores/session'

const router = useRouter()
const { t } = useI18n()

const loading = ref(false)
/** 校验失败信息固定在按钮上方，与登录页同一位置 */
const failure = ref('')

const form = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

const rules = {
  oldPassword: [{ required: true, message: '请输入当前密码' }],
  newPassword: [{ required: true, message: '请输入新密码' }],
  confirmPassword: [{ required: true, message: '请再次输入新密码' }]
}

async function submit() {
  if (loading.value) {
    return
  }
  failure.value = ''
  // 前端只判两次输入是否一致——这是纯前端能判的事。
  // 长度与字符组合交给后端：它会一次列出全部违规项，前端再写一遍就变成两套规则，改一处忘一处。
  if (form.newPassword !== form.confirmPassword) {
    failure.value = t('changePassword.mismatch')
    return
  }
  loading.value = true
  try {
    await changePassword({ oldPassword: form.oldPassword, newPassword: form.newPassword })
    // 改密只失效其他会话，当前令牌仍有效，因此不清令牌、不回登录页
    session.markPasswordChanged()
    notifySuccess(t('changePassword.success'))
    void router.replace({ name: 'dashboard' })
  } catch (error) {
    failure.value = resolveFailureMessage(error, t('changePassword.failed'))
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <AuthCard :title="t('changePassword.title')" :subtitle="t('changePassword.subtitle')">
    <Form :model="form" :rules="rules" layout="vertical" @finish="submit">
      <FormItem :label="t('changePassword.oldPassword')" name="oldPassword">
        <InputPassword
          v-model:value="form.oldPassword"
          size="large"
          autocomplete="current-password"
          :disabled="loading"
        />
      </FormItem>
      <FormItem :label="t('changePassword.newPassword')" name="newPassword">
        <InputPassword
          v-model:value="form.newPassword"
          size="large"
          autocomplete="new-password"
          :disabled="loading"
        />
        <p class="change-password__hint">{{ t('changePassword.strengthHint') }}</p>
      </FormItem>
      <FormItem :label="t('changePassword.confirmPassword')" name="confirmPassword">
        <InputPassword
          v-model:value="form.confirmPassword"
          size="large"
          autocomplete="new-password"
          :disabled="loading"
        />
      </FormItem>

      <p v-if="failure" class="change-password__failure" role="alert">{{ failure }}</p>

      <Button type="primary" size="large" html-type="submit" block :loading="loading">
        {{ t('changePassword.submit') }}
      </Button>
    </Form>
  </AuthCard>
</template>

<style scoped>
.change-password__hint {
  margin: var(--space-1) 0 0;
  font-size: var(--text-2xs);
  line-height: var(--leading-2xs);
  color: var(--color-text-tertiary);
}

.change-password__failure {
  margin: 0 0 var(--space-3);
  font-size: var(--text-xs);
  line-height: var(--leading-xs);
  color: var(--color-danger);
}
</style>
