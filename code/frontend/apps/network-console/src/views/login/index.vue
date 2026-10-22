<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { setToken } from '@yunwms/request'

const router = useRouter()
const route = useRoute()
const { t } = useI18n()

const formRef = ref<FormInstance>()
const loading = ref(false)

const form = reactive({
  account: '',
  password: ''
})

const rules: FormRules = {
  account: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }

  loading.value = true
  // 骨架阶段：后端尚未就绪，本地写入令牌以打通路由与布局
  window.setTimeout(() => {
    setToken('skeleton-token')
    loading.value = false
    ElMessage.success('登录成功（骨架演示）')
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/dashboard'
    router.replace(redirect)
  }, 300)
}
</script>

<template>
  <div class="login">
    <div class="login__panel">
      <h1 class="login__title">{{ t('login.title') }}</h1>
      <p class="login__subtitle">{{ t('app.name') }} · {{ t('app.subtitle') }}</p>

      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent>
        <el-form-item :label="t('login.account')" prop="account">
          <el-input v-model="form.account" size="large" />
        </el-form-item>
        <el-form-item :label="t('login.password')" prop="password">
          <el-input v-model="form.password" type="password" show-password size="large" />
        </el-form-item>
        <el-button type="primary" size="large" class="login__submit" :loading="loading" @click="submit">
          {{ t('login.submit') }}
        </el-button>
      </el-form>

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

.login__submit {
  width: 100%;
  margin-top: var(--space-2);
}

.login__tip {
  margin: var(--space-4) 0 0;
  font-size: var(--text-2xs);
  line-height: var(--leading-2xs);
  color: var(--color-text-tertiary);
}
</style>
