const { app, BrowserWindow, shell, ipcMain } = require('electron')
const path = require('node:path')

// 开发: 加载 vite dev server (WEB_URL); 生产: 加载打包内置的 web/dist
const WEB_URL = process.env.WEB_URL

let mainWin = null

function createWindow() {
  mainWin = new BrowserWindow({
    width: 1440,
    height: 900,
    minWidth: 1280,
    minHeight: 800,
    autoHideMenuBar: true,
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
    },
  })

  // 外部链接交给系统浏览器
  mainWin.webContents.setWindowOpenHandler(({ url }) => {
    shell.openExternal(url)
    return { action: 'deny' }
  })

  if (WEB_URL) {
    mainWin.loadURL(WEB_URL)
  } else {
    mainWin.loadFile(path.join(process.resourcesPath, 'web', 'index.html'))
  }
}

// ---------- 标签打印 ----------
// 隐藏窗口加载标签 HTML, 静默打印到指定/默认打印机
ipcMain.handle('label:print', async (_e, { html, deviceName, copies = 1 }) => {
  const win = new BrowserWindow({ show: false, webPreferences: { sandbox: true } })
  try {
    await win.loadURL('data:text/html;charset=utf-8,' + encodeURIComponent(html))
    await new Promise((resolve, reject) => {
      win.webContents.print(
        { silent: true, printBackground: true, deviceName, copies },
        (ok, reason) => (ok ? resolve() : reject(new Error(reason || '打印失败'))),
      )
    })
    return { ok: true }
  } finally {
    win.destroy()
  }
})

ipcMain.handle('label:printers', () => mainWin?.webContents.getPrintersAsync() ?? [])

// ---------- 自动更新 (electron-updater, 仅打包后生效) ----------
function setupAutoUpdate() {
  if (!app.isPackaged) return
  let autoUpdater
  try {
    autoUpdater = require('electron-updater').autoUpdater
  } catch {
    return // 依赖缺失时静默降级
  }
  const send = (type, info) => mainWin?.webContents.send('update:event', { type, info })
  autoUpdater.autoDownload = true
  autoUpdater.on('update-available', (info) => send('available', info))
  autoUpdater.on('update-not-available', () => send('none'))
  autoUpdater.on('download-progress', (p) => send('progress', p))
  autoUpdater.on('update-downloaded', (info) => send('downloaded', info))
  autoUpdater.on('error', (err) => send('error', { message: String(err?.message || err) }))
  ipcMain.handle('update:check', () => autoUpdater.checkForUpdates().catch(() => null))
  ipcMain.handle('update:install', () => autoUpdater.quitAndInstall())
  autoUpdater.checkForUpdates().catch(() => {})
}

app.whenReady().then(() => {
  createWindow()
  setupAutoUpdate()
  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) createWindow()
  })
})

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit()
})
