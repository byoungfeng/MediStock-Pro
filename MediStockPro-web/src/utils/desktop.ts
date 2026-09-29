// 桌面端 (Electron) 能力桥: 有 medistockDesktop 时走原生通道, 否则浏览器降级
const desktop = (window as any).medistockDesktop

export const isDesktop = !!desktop

/** 打印标签 HTML: 桌面端静默打印, 浏览器端新开窗口打印 */
export function printLabel(html: string): Promise<any> {
  if (desktop?.printLabel) return desktop.printLabel(html)
  const w = window.open('about:blank', '_blank', 'width=400,height=300')
  if (!w) return Promise.reject(new Error('弹窗被拦截'))
  w.document.write(html)
  w.document.close()
  w.focus()
  w.print()
  return Promise.resolve({ ok: true, fallback: true })
}

export interface BatchLabelData {
  materialName: string
  materialCode: string
  batchNo: string
  productionDate?: string
  expiryDate?: string
  spec?: string
}

/** 批次标签 HTML (60mm x 40mm 热敏标签版式) */
export function buildBatchLabel(d: BatchLabelData): string {
  const esc = (s: any) => String(s ?? '-').replace(/[<>&"]/g, (c) => ({ '<': '&lt;', '>': '&gt;', '&': '&amp;', '"': '&quot;' }[c]!))
  return `<!DOCTYPE html><html><head><meta charset="utf-8"><style>
    @page { size: 60mm 40mm; margin: 0; }
    body { width: 60mm; height: 40mm; margin: 0; padding: 2mm; box-sizing: border-box;
           font-family: "Microsoft YaHei", sans-serif; }
    .name { font-size: 11pt; font-weight: 700; line-height: 1.2; overflow: hidden;
            display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; }
    .row { font-size: 8pt; margin-top: 1mm; display: flex; justify-content: space-between; }
    .code { font-size: 9pt; font-weight: 700; letter-spacing: 1px; margin-top: 1mm;
            border-top: 1px dashed #000; padding-top: 1mm; }
  </style></head><body>
    <div class="name">${esc(d.materialName)}</div>
    <div class="row"><span>规格: ${esc(d.spec)}</span><span>批号: ${esc(d.batchNo)}</span></div>
    <div class="row"><span>生产: ${esc(d.productionDate)}</span><span>效期: ${esc(d.expiryDate)}</span></div>
    <div class="code">${esc(d.materialCode)}</div>
  </body></html>`
}
