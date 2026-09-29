const { contextBridge, ipcRenderer } = require('electron')

// 桌面能力桥: 标签打印 / 自动更新 (扫码枪为键盘楔, 渲染端直接监听键盘事件)
contextBridge.exposeInMainWorld('medistockDesktop', {
  platform: process.platform,
  versions: {
    electron: process.versions.electron,
    chrome: process.versions.chrome,
  },
  /** 静默打印标签 HTML 到指定/默认打印机 */
  printLabel: (html, options = {}) => ipcRenderer.invoke('label:print', { html, ...options }),
  /** 可用打印机列表 */
  listPrinters: () => ipcRenderer.invoke('label:printers'),
  /** 手动检查更新 */
  checkForUpdates: () => ipcRenderer.invoke('update:check'),
  /** 下载完成后重启安装 */
  installUpdate: () => ipcRenderer.invoke('update:install'),
  /** 订阅更新事件: available/none/progress/downloaded/error, 返回取消订阅函数 */
  onUpdateEvent: (cb) => {
    const listener = (_e, payload) => cb(payload)
    ipcRenderer.on('update:event', listener)
    return () => ipcRenderer.removeListener('update:event', listener)
  },
})
