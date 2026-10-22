type NotifyKind = 'success' | 'error'
type NotifyHandler = (kind: NotifyKind, content: string) => void

let handler: NotifyHandler | undefined

/** 具体实现由 AppBootstrap 注入；未注入时降级到 console，避免提示被静默吞掉 */
export function setNotifyHandler(next: NotifyHandler): void {
  handler = next
}

export function notifySuccess(content: string): void {
  if (handler) {
    handler('success', content)
    return
  }
  console.info('[notify]', content)
}

export function notifyError(content: string): void {
  if (handler) {
    handler('error', content)
    return
  }
  console.error('[notify]', content)
}
