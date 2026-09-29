<template>
  <div class="login">
    <!-- 左: 品牌视觉区 -->
    <section class="showcase">
      <div class="showcase-bg" aria-hidden="true"></div>
      <div class="showcase-shade" aria-hidden="true"></div>

      <header class="showcase-head">
        <span class="logo-m" aria-hidden="true">
          <svg viewBox="0 0 48 48" width="48" height="48">
            <defs>
              <linearGradient id="mGrad" x1="0%" y1="0%" x2="100%" y2="100%">
                <stop offset="0%" stop-color="#60a5fa" />
                <stop offset="100%" stop-color="#2563eb" />
              </linearGradient>
            </defs>
            <path d="M8 36V12h7l9 14 9-14h7v24h-6V22l-8 12h-4l-8-12v14H8z" fill="url(#mGrad)" />
            <rect x="34" y="30" width="6" height="6" rx="1.5" fill="#38bdf8" />
          </svg>
        </span>
        <div class="head-text">
          <h1 class="brand-title">MediStock <span class="brand-pro">Pro</span></h1>
          <p class="brand-sub">医院进销存管理系统</p>
        </div>
      </header>

      <div class="orbit-wrap" aria-hidden="true">
        <div class="orbit-ring"></div>
        <div class="orbit-core">
          <svg viewBox="0 0 24 24" width="28" height="28">
            <rect x="9.5" y="4" width="5" height="16" rx="1.5" fill="currentColor" />
            <rect x="4" y="9.5" width="16" height="5" rx="1.5" fill="currentColor" />
          </svg>
        </div>
        <div
          v-for="(m, i) in modules"
          :key="m.label"
          class="orbit-node"
          :style="nodeStyle(i)"
        >
          <span class="node-icon"><component :is="m.icon" /></span>
          <span class="node-label">{{ m.label }}</span>
        </div>
      </div>

      <footer class="showcase-foot">
        <div class="wave" aria-hidden="true"></div>
        <p class="foot-slogan">用科技守护每一份医疗资源</p>
      </footer>
    </section>

    <!-- 右: 登录表单 -->
    <section class="form-side">
      <div class="form-card">
        <h2 class="form-title">登录</h2>
        <p class="form-sub">欢迎使用 <strong>MediStock Pro</strong></p>

        <label class="field">
          <Input size="large" placeholder="请输入用户名" :value="form.username"
                 :prefix="h(IconUser)" :on-change="(v: string) => (form.username = v)" />
        </label>
        <label class="field">
          <Input size="large" :type="showPassword ? 'text' : 'password'" placeholder="请输入密码"
                 :value="form.password" :prefix="h(IconLock)"
                 :suffix="h(passwordToggle)"
                 :on-change="(v: string) => (form.password = v)" :on-enter="doLogin" />
        </label>
        <label class="field captcha-field">
          <Input size="large" placeholder="请输入验证码" :value="form.captcha"
                 :prefix="h(IconShield)" :on-change="(v: string) => (form.captcha = v)"
                 :on-enter="doLogin" />
          <button type="button" class="captcha-box" title="点击刷新验证码" @click="refreshCaptcha">
            {{ captchaDisplay }}
          </button>
        </label>

        <div class="form-row">
          <Checkbox :checked="remember"
                    :on-change="(e: any) => (remember = e.target.checked)">记住账号</Checkbox>
          <a class="forgot" @click.prevent="onForgot">忘记密码？</a>
        </div>

        <Button class="submit" theme="solid" type="primary" size="large" block
                :loading="loading" :on-click="doLogin">
          <span class="submit-inner">登录 <IconArrowRight class="submit-arrow" /></span>
        </Button>

        <p class="form-copy">© 2026 MediStock Pro · 医院进销存管理系统 v3.0</p>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { h, reactive, ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { Button, Checkbox, Input, Toast } from '@kousum/semi-ui-vue'
import {
  IconUser,
  IconLock,
  IconShield,
  IconEyeOpened,
  IconEyeClosed,
  IconArrowRight,
  IconCart,
  IconBox,
  IconHistogram,
  IconFile,
} from '@kousum/semi-icons-vue'
import { login, getUserInfo } from '@/api/auth'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const form = reactive({ username: '', password: '', captcha: '' })
const loading = ref(false)
const remember = ref(false)
const showPassword = ref(false)
const captchaCode = ref('')
const captchaDisplay = computed(() => captchaCode.value.split('').join(' '))
const REMEMBER_KEY = 'medistock.login.username'

const modules = [
  { icon: IconCart, label: '采购管理' },
  { icon: IconBox, label: '库存管理' },
  { icon: IconShield, label: '效期预警' },
  { icon: IconHistogram, label: '数据报表' },
  { icon: IconFile, label: '科室领用' },
]

function nodeStyle(index: number) {
  const angle = -90 + index * 72
  const radius = 168
  const x = Math.cos((angle * Math.PI) / 180) * radius
  const y = Math.sin((angle * Math.PI) / 180) * radius
  return {
    transform: `translate(calc(-50% + ${x}px), calc(-50% + ${y}px))`,
  }
}

function refreshCaptcha() {
  captchaCode.value = String(Math.floor(1000 + Math.random() * 9000))
  form.captcha = ''
}

function passwordToggle() {
  const Icon = showPassword.value ? IconEyeClosed : IconEyeOpened
  return h(Icon, {
    class: 'pwd-toggle',
    onClick: () => {
      showPassword.value = !showPassword.value
    },
  })
}

onMounted(() => {
  refreshCaptcha()
  const saved = localStorage.getItem(REMEMBER_KEY)
  if (saved) {
    form.username = saved
    remember.value = true
  }
})

function onForgot() {
  Toast.info('请联系系统管理员重置密码')
}

async function doLogin() {
  if (!form.username || !form.password) {
    Toast.warning('请输入用户名和密码')
    return
  }
  if (form.captcha !== captchaCode.value) {
    Toast.warning('验证码错误')
    refreshCaptcha()
    return
  }
  loading.value = true
  try {
    const data: any = await login({ username: form.username, password: form.password })
    userStore.setLogin(data.token, data.user)
    const info: any = await getUserInfo()
    userStore.setUserInfo(info)
    if (remember.value) {
      localStorage.setItem(REMEMBER_KEY, form.username)
    } else {
      localStorage.removeItem(REMEMBER_KEY)
    }
    router.push((route.query.redirect as string) || '/')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login {
  height: 100%;
  min-height: 100vh;
  display: flex;
  background: #eef2f7;
  overflow: hidden;
}

/* ---------- 左: 品牌视觉 ---------- */
.showcase {
  flex: 1.12;
  min-width: 0;
  position: relative;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding: 36px 44px 32px;
  overflow: hidden;
}
/* 纯实景底图 + 轻遮罩（勿用含 UI 的原型整图，避免重影） */
.showcase-bg {
  position: absolute;
  inset: -20px;
  background-image: url('@/assets/login-bg.jpg');
  background-size: cover;
  background-position: center 35%;
  background-repeat: no-repeat;
  filter: blur(1.5px) saturate(1.05);
  transform: scale(1.03);
}
.showcase-shade {
  position: absolute;
  inset: 0;
  background:
    linear-gradient(118deg, rgba(255, 255, 255, 0.82) 0%, rgba(255, 255, 255, 0.28) 34%, rgba(219, 234, 254, 0.14) 58%, rgba(15, 23, 42, 0.32) 100%),
    radial-gradient(ellipse 58% 46% at 48% 50%, rgba(59, 130, 246, 0.14) 0%, transparent 72%);
}
.showcase-head,
.orbit-wrap,
.showcase-foot {
  position: relative;
  z-index: 1;
}
.showcase-head {
  display: flex;
  align-items: flex-start;
  gap: 14px;
  animation: rise-fade 0.7s cubic-bezier(0.16, 1, 0.3, 1) both;
}
.logo-m {
  flex-shrink: 0;
  filter: drop-shadow(0 4px 12px rgba(37, 99, 235, 0.45));
}
.brand-title {
  margin: 0;
  font-size: 30px;
  font-weight: 700;
  letter-spacing: -0.02em;
  color: #16295e;
}
.brand-pro {
  color: #2563eb;
}
.brand-sub {
  margin: 4px 0 0;
  font-size: 15px;
  font-weight: 500;
  color: #334155;
}

/* HUD 模块环 */
.orbit-wrap {
  position: absolute;
  left: 50%;
  top: 46%;
  width: 0;
  height: 0;
  z-index: 1;
  animation: rise-fade 0.8s cubic-bezier(0.16, 1, 0.3, 1) 0.08s both;
}
.orbit-ring {
  position: absolute;
  left: 50%;
  top: 50%;
  width: 340px;
  height: 340px;
  transform: translate(-50%, -50%);
  border-radius: 50%;
  border: 1px solid rgba(96, 165, 250, 0.4);
  box-shadow:
    0 0 0 1px rgba(255, 255, 255, 0.3) inset,
    0 0 48px rgba(59, 130, 246, 0.22);
  background: radial-gradient(circle, rgba(255, 255, 255, 0.1) 0%, rgba(59, 130, 246, 0.06) 45%, transparent 70%);
}
.orbit-ring::before {
  content: '';
  position: absolute;
  inset: 24px;
  border-radius: 50%;
  border: 1px dashed rgba(147, 197, 253, 0.5);
}
.orbit-core {
  position: absolute;
  left: 50%;
  top: 50%;
  width: 56px;
  height: 56px;
  transform: translate(-50%, -50%);
  border-radius: 50%;
  background: linear-gradient(150deg, rgba(96, 165, 250, 0.95) 0%, rgba(37, 99, 235, 0.92) 100%);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow:
    0 6px 20px rgba(37, 99, 235, 0.35),
    inset 0 1px 0 rgba(255, 255, 255, 0.35);
  border: 1px solid rgba(255, 255, 255, 0.55);
  backdrop-filter: blur(4px);
}
.orbit-node {
  position: absolute;
  left: 50%;
  top: 50%;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  width: 88px;
  text-align: center;
}
.node-icon {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.22);
  border: 1px solid rgba(191, 219, 254, 0.65);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  color: #1d4ed8;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  box-shadow: 0 6px 20px rgba(37, 99, 235, 0.25);
}
.node-label {
  font-size: 11px;
  font-weight: 500;
  color: #f8fafc;
  text-shadow: 0 1px 6px rgba(15, 23, 42, 0.55);
}

.showcase-foot {
  margin-top: auto;
  position: relative;
  z-index: 2;
  width: 100%;
  flex-shrink: 0;
  padding-bottom: 8px;
}
.wave {
  position: absolute;
  left: -80px;
  right: -20px;
  bottom: -10px;
  height: 120px;
  background:
    radial-gradient(90% 100% at 20% 100%, rgba(37, 99, 235, 0.7) 0%, transparent 55%),
    radial-gradient(70% 90% at 55% 110%, rgba(59, 130, 246, 0.5) 0%, transparent 60%);
  pointer-events: none;
  z-index: 1;
}
.foot-slogan {
  position: relative;
  z-index: 2;
  display: inline-block;
  margin: 0 0 0 12px;
  white-space: nowrap;
  font-size: 22px;
  font-weight: 400;
  line-height: 1.4;
  letter-spacing: 0.05em;
  font-family: "STXingkai", "KaiTi", "FangSong", "Segoe Script", cursive;
  color: rgba(255, 255, 255, 0.98);
  text-shadow: 0 2px 12px rgba(15, 23, 42, 0.5);
}

/* ---------- 右: 表单 ---------- */
.form-side {
  flex: 0.88;
  min-width: 440px;
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 48px 64px;
  background: #f3f6fb;
}

.form-card {
  width: 100%;
  max-width: 420px;
  padding: 40px 36px 32px;
  border-radius: 14px;
  background: #ffffff;
  border: 1px solid #e8edf5;
  box-shadow: 0 16px 48px rgba(15, 23, 42, 0.07);
  animation: rise-fade 0.7s cubic-bezier(0.16, 1, 0.3, 1) 0.12s both;
}
.form-title {
  margin: 0;
  font-size: 32px;
  font-weight: 700;
  letter-spacing: -0.02em;
  color: #0f172a;
}
.form-sub {
  margin: 10px 0 30px;
  font-size: 14px;
  color: #64748b;
}
.form-sub strong {
  color: #2563eb;
  font-weight: 600;
}

.field { display: block; margin-bottom: 16px; }
.field :deep(.semi-input-wrapper) {
  height: 48px;
  border-radius: 10px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
}
.field :deep(.semi-input-wrapper:hover) {
  border-color: #cbd5e1;
  background: #fff;
}
.field :deep(.semi-input-wrapper-focus),
.field :deep(.semi-input-wrapper.semi-input-wrapper-focus) {
  background: #fff;
  border-color: #3b82f6 !important;
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.12) !important;
}
.field :deep(.semi-input-prefix) { color: #94a3b8; margin-right: 8px; }
.field :deep(.semi-input-suffix) { color: #94a3b8; cursor: pointer; }
.field :deep(.semi-input) { color: #0f172a; }
.field :deep(.semi-input::placeholder) { color: #94a3b8; }

.captcha-field {
  display: flex;
  align-items: stretch;
  gap: 10px;
}
.captcha-field :deep(.semi-input-wrapper) { flex: 1; }
.captcha-box {
  flex-shrink: 0;
  width: 112px;
  border: 1px solid #c7d9f5;
  border-radius: 10px;
  background:
    repeating-linear-gradient(-12deg, rgba(37, 99, 235, 0.04) 0 2px, transparent 2px 6px),
    linear-gradient(135deg, #eef4ff 0%, #e3ebff 100%);
  font-size: 18px;
  font-weight: 700;
  letter-spacing: 0.12em;
  color: #1e40af;
  cursor: pointer;
  font-variant-numeric: tabular-nums;
  transition: transform 0.15s ease, box-shadow 0.15s ease;
}
.captcha-box:hover {
  box-shadow: 0 4px 12px rgba(37, 99, 235, 0.15);
}
.captcha-box:active { transform: scale(0.98); }

.form-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 6px 0 22px;
}
.form-row :deep(.semi-checkbox) { font-size: 13px; color: #475569; }
.forgot {
  font-size: 13px;
  color: #2563eb;
  cursor: pointer;
  text-decoration: none;
}
.forgot:hover { color: #1d4ed8; }

.submit {
  height: 48px;
  border-radius: 10px;
  border: none;
  background: linear-gradient(135deg, #3b82f6 0%, #2563eb 55%, #1d4ed8 100%);
  box-shadow: 0 10px 24px rgba(37, 99, 235, 0.35);
}
.submit:hover {
  background: linear-gradient(135deg, #60a5fa 0%, #3b82f6 55%, #2563eb 100%);
  box-shadow: 0 12px 28px rgba(37, 99, 235, 0.42);
}
.submit-inner {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 600;
}
.submit-arrow { font-size: 18px; }

.form-copy {
  margin: 22px 0 0;
  font-size: 11px;
  color: #94a3b8;
  text-align: center;
}

:deep(.pwd-toggle) {
  cursor: pointer;
}

@keyframes rise-fade {
  from { opacity: 0; transform: translateY(14px); }
  to { opacity: 1; transform: translateY(0); }
}
@media (prefers-reduced-motion: reduce) {
  .showcase-head,
  .orbit-wrap,
  .form-card {
    animation: none;
  }
}
</style>
