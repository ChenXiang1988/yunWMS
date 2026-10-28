<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import {
  Button,
  Descriptions,
  DescriptionsItem,
  Form,
  FormItem,
  Input,
  InputPassword,
  Tag,
  type FormInstance
} from 'ant-design-vue'
import { openOwnerAccount, type OwnerStatus } from '@/api/owner'
import { notifySuccess } from '@/api/notify'
import {
  ACCOUNT_DISPLAY_NAME_MAX,
  buildOpenAccountPayload,
  canOpenOwnerAccount,
  emptyAccountForm,
  type AccountFormValues
} from '../account-model'
import { ownerStatusText } from '../owner-status'

/**
 * 账号页签：展示本次开通的主账号，并提供「开通主账号」入口。
 *
 * 四条由契约与台账决定的取舍：
 *
 * 1. 页签打开时不发请求。台账里没有按货主查询主账号的端点（`POST /owners/{id}/account`
 *    只有写，没有读；用户列表也不支持按货主过滤），所以这里不假装知道历史账号：页签只
 *    展示**本次会话内**开通成功的结果，登录名取自开号响应的 `loginName`。拿用户列表全量
 *    拉取再在前端筛，会把「有没有开号」变成一个可能过时的推断，不做。
 * 2. 开号是一次性动作：成功后入口换成已开通卡片，不再给第二次入口。失败不判业务码、
 *    不匹配文案（请求层只透出后端中文消息），表单保持打开供修改——密码策略不符这类
 *    可以改完再提交。
 * 3. 初始密码不落前端状态：提交结束（无论成败）立即清空，不写存储、不回显、不进日志。
 *    密码规则（长度与字符组合）不在前端复述，只校验非空，后端是唯一判定者。
 * 4. 不提供改绑与改登录名：账号归属唯一且登录名由系统按货主编码生成，这是后端的硬约束，
 *    做出入口就是误导。入口置灰只看货主状态（仅「合作中」可开号），是为了不让人白填
 *    一次必然被拒的表单，不是规则的第二真源。
 *
 * 页内区块而非 Modal：详情抽屉之上再叠浮层会落到抽屉遮罩下方（见
 * `use-owner-lifecycle.ts` 的 `editFromDetail`），与联系人、税号页签同一处理。
 */
const props = defineProps<{
  /** 所属货主标识，开号接口的路径参数 */
  ownerId: string
  /** 货主系统编码：登录名由它生成，表单里只读展示 */
  ownerCode: string
  /** 货主状态：只有合作中可开号，非合作中把入口置灰并说明原因 */
  status: OwnerStatus
}>()

/** 本次会话内开通成功的主账号登录名；null 表示本页签还没开出账号 */
const openedLoginName = ref<string | null>(null)

const formRef = ref<FormInstance>()
const formValues = reactive<AccountFormValues>(emptyAccountForm())
const formOpen = ref(false)
const submitting = ref(false)

const canOpen = computed(() => canOpenOwnerAccount(props.status, openedLoginName.value))

/** 入口置灰的原因：说清为什么点不动，而不是让人反复试 */
const gateHint = computed(() =>
  props.status === 'ACTIVE'
    ? null
    : `仅「合作中」的货主可开通主账号，当前状态为${ownerStatusText(props.status)}`
)

/**
 * 校验规则：只做必填与长度。
 *
 * 初始密码只校验非空——长度与字符组合是后端的密码策略，前端复述一遍就成了第二份真源，
 * 两处一旦漂移，要么拦住后端本可接受的密码，要么放过必然被拒的密码。
 * 展示名的 `whitespace: true` 对应后端「只填空白按未填处理」。
 */
const rules = {
  displayName: [
    { required: true, whitespace: true, message: '请输入主账号姓名' },
    {
      max: ACCOUNT_DISPLAY_NAME_MAX,
      message: `主账号姓名不能超过 ${ACCOUNT_DISPLAY_NAME_MAX} 个字符`
    }
  ],
  initialPassword: [{ required: true, message: '请输入初始密码' }]
}

function openForm(): void {
  if (!canOpen.value) {
    return
  }
  Object.assign(formValues, emptyAccountForm())
  formOpen.value = true
  formRef.value?.clearValidate()
}

/** 收起表单并清掉明文：密码不留在组件状态里 */
function closeForm(): void {
  formOpen.value = false
  formValues.initialPassword = ''
}

/** 校验通过后把表单值拷一份交出去，避免提交期间用户继续打字改到已在途的请求体 */
async function validate(): Promise<AccountFormValues | null> {
  try {
    await formRef.value?.validate()
    return { ...formValues }
  } catch {
    // antd 的 validate 以 reject 表示校验不通过，错误已由表单项就地展示
    return null
  }
}

async function submit(): Promise<void> {
  const values = await validate()
  if (!values) {
    return
  }
  const payload = buildOpenAccountPayload(values)
  if (!payload) {
    return
  }
  submitting.value = true
  try {
    const account = await openOwnerAccount(props.ownerId, payload)
    openedLoginName.value = account.loginName
    formOpen.value = false
    notifySuccess(`主账号已开通，登录名 ${account.loginName}`)
  } catch (error) {
    // 40900（已有主账号、货主非合作中等）与密码策略不符都由请求层把后端中文消息弹给用户；
    // 文案与业务码都不在这里判，表单保持打开供修改。
    // 只记 message 不记整个错误对象：Axios 失败时错误对象带着请求体，里面有初始密码明文
    console.error('[owner] 开通主账号失败', error instanceof Error ? error.message : '未知错误')
  } finally {
    submitting.value = false
    // 明文只在这次请求里出现，提交结束立即清掉：不写存储、不回显、不进日志
    formValues.initialPassword = ''
  }
}

watch(
  () => props.ownerId,
  () => {
    // 切到另一个货主时清空：开号结果与表单里填的值都属于上一个货主
    openedLoginName.value = null
    formOpen.value = false
    Object.assign(formValues, emptyAccountForm())
  }
)
</script>

<template>
  <div class="account">
    <section v-if="openedLoginName" class="account__card">
      <header class="account__card-head">
        <span class="account__card-title">主账号</span>
        <Tag color="success">已开通</Tag>
      </header>
      <Descriptions :column="1" bordered size="small">
        <DescriptionsItem label="登录名">
          <span class="account__mono">{{ openedLoginName }}</span>
        </DescriptionsItem>
      </Descriptions>
      <p class="account__hint">
        初始密码不在界面回显，请通过安全渠道交给货主。账号首次登录须先修改密码；登录名与
        归属创建后不可修改，本页签不提供改绑与改名。
      </p>
    </section>

    <section v-else class="account__entry">
      <p class="account__lead">
        登录名由系统按货主编码生成（<span class="account__mono">{{ ownerCode }}</span>），
        创建后不可修改，账号也不能改绑到其他货主。主账号一经开通不可重复开通；若提交时
        提示已存在，说明该货主此前已开号，提示里会给出已有的登录名。
      </p>
      <div class="account__toolbar">
        <Button type="primary" :disabled="!canOpen" @click="openForm">开通主账号</Button>
      </div>
      <p v-if="gateHint" class="account__hint">{{ gateHint }}</p>
    </section>

    <section v-if="formOpen" class="account__form">
      <h4 class="account__form-title">开通主账号</h4>

      <Form ref="formRef" :model="formValues" :rules="rules" layout="vertical">
        <FormItem label="登录名">
          <Input :value="ownerCode" disabled />
        </FormItem>
        <FormItem label="主账号姓名" name="displayName">
          <Input
            v-model:value="formValues.displayName"
            :maxlength="ACCOUNT_DISPLAY_NAME_MAX"
            placeholder="登录货主端后显示在页头"
            allow-clear
          />
        </FormItem>
        <FormItem label="初始密码" name="initialPassword">
          <InputPassword
            v-model:value="formValues.initialPassword"
            autocomplete="new-password"
            placeholder="由操作人设定，提交后不再回显"
          />
        </FormItem>
      </Form>

      <p class="account__form-hint">
        账号被标记为首次登录须先修改密码；密码的长度与字符要求由服务端校验，不符时会返回
        具体原因。
      </p>

      <div class="account__form-actions">
        <Button @click="closeForm">取消</Button>
        <Button type="primary" :loading="submitting" @click="submit">开通</Button>
      </div>
    </section>
  </div>
</template>

<style scoped>
.account__card {
  padding: var(--space-4);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
}

.account__card-head {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  margin-bottom: var(--space-3);
}

.account__card-title {
  font-size: var(--font-size-base);
  font-weight: var(--weight-medium);
  color: var(--color-text-primary);
}

.account__lead {
  margin: 0;
  font-size: var(--font-size-base);
  line-height: var(--leading-base);
  color: var(--color-text-secondary);
}

.account__toolbar {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--space-3);
}

.account__hint {
  margin: var(--space-2) 0 0;
  font-size: var(--text-xs);
  line-height: var(--leading-xs);
  color: var(--color-text-tertiary);
}

.account__form {
  margin-top: var(--space-3);
  padding: var(--space-4);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg-subtle);
}

.account__form-title {
  margin: 0 0 var(--space-3);
  font-size: var(--font-size-base);
  font-weight: var(--weight-medium);
  color: var(--color-text-secondary);
}

.account__form-hint {
  margin: var(--space-1) 0 0;
  font-size: var(--text-xs);
  line-height: var(--leading-xs);
  color: var(--color-text-tertiary);
}

.account__form-actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--space-2);
  margin-top: var(--space-3);
}

.account__mono {
  font-family: var(--font-mono);
}
</style>
