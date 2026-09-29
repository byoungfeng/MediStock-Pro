// 扫码枪 (键盘楔 HID): 快速连续击键 + Enter 结尾, 与人工打字区分
// 判定: 相邻击键间隔 < 50ms, 码长 >= 4, Enter 提交; 输入框聚焦时不劫持 (走正常输入)

const MAX_INTERVAL_MS = 50
const MIN_CODE_LEN = 4

export type ScanHandler = (code: string) => void

/** 注册全局扫码监听, 返回取消注册函数 */
export function onScan(handler: ScanHandler): () => void {
  let buffer = ''
  let lastAt = 0

  const onKeydown = (e: KeyboardEvent) => {
    const target = e.target as HTMLElement | null
    const tag = target?.tagName
    if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT' || target?.isContentEditable) {
      buffer = ''
      return
    }
    const now = Date.now()
    if (e.key === 'Enter') {
      if (buffer.length >= MIN_CODE_LEN && now - lastAt < MAX_INTERVAL_MS * 4) {
        handler(buffer)
        e.preventDefault()
      }
      buffer = ''
      lastAt = 0
      return
    }
    if (e.key.length !== 1) return // 忽略 Shift/Ctrl 等功能键
    buffer = now - lastAt < MAX_INTERVAL_MS ? buffer + e.key : e.key
    lastAt = now
  }

  window.addEventListener('keydown', onKeydown, true)
  return () => window.removeEventListener('keydown', onKeydown, true)
}
