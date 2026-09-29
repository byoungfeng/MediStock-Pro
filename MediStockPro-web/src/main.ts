import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import { vPermission } from './directives/permission'

// semi-ui-vue 组件按需自动引入各自样式, 无需全量 css
import './styles/global.css'

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.directive('permission', vPermission)
app.mount('#app')