// 数字排版统一: 等宽数字由全局 CSS 保证, 此处负责千分位与右对齐渲染
// PRODUCT.md: 库存数量、金额、批号、效期是界面主角, 必须等宽数字、右对齐、千分位

/** 数量: 千分位整数 (空值显示 0) */
export function fmtNum(v: unknown): string {
  const n = Number(v)
  return isNaN(n) ? '0' : n.toLocaleString('zh-CN')
}

/** 金额/成本: 千分位两位小数 */
export function fmtMoney(v: unknown): string {
  const n = Number(v)
  return isNaN(n) ? '0.00' : n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

interface NumColumn {
  title: string
  dataIndex: string
  align: 'right'
  render: (v: unknown) => string
}

/** 数量列: 右对齐 + 千分位 */
export function numCol(title: string, dataIndex: string, extra: Record<string, unknown> = {}): NumColumn {
  return { title, dataIndex, align: 'right', render: (v: unknown) => fmtNum(v), ...extra }
}

/** 金额列: 右对齐 + 千分位两位小数 */
export function moneyCol(title: string, dataIndex: string, extra: Record<string, unknown> = {}): NumColumn {
  return { title, dataIndex, align: 'right', render: (v: unknown) => fmtMoney(v), ...extra }
}
