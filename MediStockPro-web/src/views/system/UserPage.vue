<template>
  <div class="page-card">
    <div class="seg-tabs">
      <div v-for="t in tabs" :key="t.key" class="seg-tab" :class="{ active: tab === t.key }"
           @click="switchTab(t.key)">{{ t.text }}</div>
    </div>

    <!-- ==================== 用户 ==================== -->
    <template v-if="tab === 'users'">
      <div class="toolbar">
        <Input class="w200" :value="userQuery.keyword" :on-change="(v: string) => (userQuery.keyword = v)"
               placeholder="用户名 / 姓名" @keyup.enter="loadUsers" />
        <Button theme="solid" type="primary" :on-click="loadUsers">查询</Button>
        <Button v-permission="PERMISSION.USER_MANAGE_EDIT" type="primary" theme="light" :on-click="openCreateUser">新建用户</Button>
      </div>
      <Table :columns="userColumns" :data-source="userRows" row-key="id" size="small" :loading="loading"
             :pagination="{ currentPage: userQuery.page, pageSize: userQuery.size, total: userTotal, onChange: (p: number) => { userQuery.page = p; loadUsers() } }" />
    </template>

    <!-- ==================== 角色 ==================== -->
    <template v-if="tab === 'roles'">
      <div class="toolbar">
        <Button theme="solid" type="primary" :on-click="loadRoles">刷新</Button>
        <Button v-permission="PERMISSION.ROLE_MANAGE_EDIT" type="primary" theme="light" :on-click="openCreateRole">新建角色</Button>
      </div>
      <Table :columns="roleColumns" :data-source="roleRows" row-key="id" size="small" :loading="loading" :pagination="false" />
    </template>

    <!-- 新建用户 -->
    <Modal :visible="userVisible" title="新建用户" :width="520"
           :on-ok="saveUser" :on-cancel="() => { userVisible  = false }" :confirm-loading="saving">
      <div class="form-grid">
        <label>用户名 *</label>
        <Input :value="userForm.username" :on-change="(v: string) => (userForm.username = v)" />
        <label>初始密码 *</label>
        <Input :value="userForm.password" :on-change="(v: string) => (userForm.password = v)" type="password" />
        <label>姓名 *</label>
        <Input :value="userForm.name" :on-change="(v: string) => (userForm.name = v)" />
        <label>角色</label>
        <Select :value="userForm.roleIds" :option-list="roleOptions" multiple
                :on-change="(v: any) => (userForm.roleIds = v)" />
      </div>
    </Modal>

    <!-- 分配角色 -->
    <Modal :visible="assignVisible" :title="`分配角色: ${assignUser?.username || ''}`" :width="440"
           :on-ok="saveAssign" :on-cancel="() => { assignVisible  = false }" :confirm-loading="saving">
      <Select :value="assignRoleIds" :option-list="roleOptions" multiple style="width: 100%"
              :on-change="(v: any) => (assignRoleIds = v)" />
    </Modal>

    <!-- 新建角色 -->
    <Modal :visible="roleVisible" title="新建角色" :width="520"
           :on-ok="saveRole" :on-cancel="() => { roleVisible  = false }" :confirm-loading="saving">
      <div class="form-grid">
        <label>编码 *</label>
        <Input :value="roleForm.code" :on-change="(v: string) => (roleForm.code = v)" placeholder="如 PHARMACIST" />
        <label>名称 *</label>
        <Input :value="roleForm.name" :on-change="(v: string) => (roleForm.name = v)" />
        <label>数据范围</label>
        <Select :value="roleForm.dataScope" :option-list="scopeOptions"
                :on-change="(v: any) => (roleForm.dataScope = v)" />
        <label>备注</label>
        <Input :value="roleForm.remark" :on-change="(v: string) => (roleForm.remark = v)" />
      </div>
    </Modal>

    <!-- 配置权限: 按菜单模块分组, 支持组内全选 -->
    <Modal :visible="permVisible" :title="`配置权限: ${permRole?.name || ''}`" :width="760"
           :on-ok="savePerms" :on-cancel="() => { permVisible  = false }" :confirm-loading="saving">
      <div class="perm-groups">
        <div v-for="g in permGroups" :key="g.title" class="perm-group">
          <div class="perm-group-head" @click="toggleGroup(g)">
            <input type="checkbox" :checked="g.checkedCount === g.items.length && g.items.length > 0"
                   :indeterminate="g.checkedCount > 0 && g.checkedCount < g.items.length"
                   @click.stop @change="toggleGroup(g)" />
            <span class="perm-group-title">{{ g.title }}</span>
            <span class="perm-group-count">{{ g.checkedCount }}/{{ g.items.length }}</span>
          </div>
          <div class="perm-grid">
            <label v-for="p in g.items" :key="p.value" class="perm-item">
              <input type="checkbox" :checked="permChecked.includes(p.value)" @change="togglePerm(p.value)" />
              <span>{{ p.label }}</span>
            </label>
          </div>
        </div>
      </div>
    </Modal>

    <!-- 数据权限 P008 -->
    <Modal :visible="scopeVisible" :title="`数据权限: ${scopeRole?.name || ''}`" :width="520"
           :on-ok="saveScope" :on-cancel="() => { scopeVisible = false }" :confirm-loading="saving">
      <div class="form-grid">
        <label>数据范围</label>
        <Select :value="scopeForm.dataScope" :option-list="scopeOptions" style="width: 100%"
                :on-change="(v: any) => (scopeForm.dataScope = v)" />
        <template v-if="scopeForm.dataScope === 'WAREHOUSE'">
          <label>可见仓库</label>
          <Select :value="scopeForm.warehouseIds" :option-list="warehouseOptions" multiple style="width: 100%"
                  :on-change="(v: any) => (scopeForm.warehouseIds = v)" />
        </template>
      </div>
    </Modal>
  </div>
</template>

<script setup lang="ts">
import { computed, h, onMounted, reactive, ref } from 'vue'
import { Button, Input, Modal, Select, Table, Tag, Toast } from '@kousum/semi-ui-vue'
import {
  assignDataScope, assignRolePerms, assignUserRoles, createRole, createUser,
  getRoleWarehouses, listPermissions, listRoles, listWarehouses, pageUsers,
} from '@/api/system'
import { PERMISSION, PERMISSION_GROUPS } from '@/constants/permissions'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()

const loading = ref(false)
const saving = ref(false)
const tab = ref('users')
const tabs = [
  { key: 'users', text: '用户管理' },
  { key: 'roles', text: '角色权限' },
]

// ==================== 用户 ====================
const userRows = ref<any[]>([])
const userTotal = ref(0)
const userQuery = reactive({ page: 1, size: 20, keyword: '' })
const roleOptions = ref<any[]>([])

function roleNames(roleIds: number[]) {
  return (roleIds || [])
    .map((id) => roleOptions.value.find((r) => r.value === id)?.label || id)
    .join('、') || '-'
}

const userColumns = [
  { title: '用户名', dataIndex: 'username' },
  { title: '姓名', dataIndex: 'name' },
  { title: '电话', dataIndex: 'phone' },
  {
    title: '角色', dataIndex: 'roleIds',
    render: (_: any, r: any) => roleNames(r.roleIds),
  },
  {
    title: '状态', dataIndex: 'status',
    render: (_: any, r: any) => h(Tag, { color: r.status === 1 ? 'green' : 'grey' }, () => (r.status === 1 ? '启用' : '停用')),
  },
  {
    title: '操作',
    render: (_: any, r: any) => userStore.hasPermission(PERMISSION.USER_MANAGE_EDIT) && h(Button, { size: 'small', theme: 'light', onClick: () => openAssign(r) }, () => '分配角色'),
  },
]

async function loadUsers() {
  loading.value = true
  try {
    const res: any = await pageUsers({ ...userQuery })
    userRows.value = res.records
    userTotal.value = res.total
  } finally {
    loading.value = false
  }
}

const userVisible = ref(false)
const userForm = reactive({ username: '', password: '', name: '', roleIds: [] as number[] })
function openCreateUser() {
  Object.assign(userForm, { username: '', password: '', name: '', roleIds: [] })
  userVisible.value = true
}
async function saveUser() {
  if (!userForm.username || !userForm.password || !userForm.name) {
    Toast.warning('用户名/密码/姓名必填')
    return
  }
  saving.value = true
  try {
    await createUser(userForm)
    Toast.success('用户已创建')
    userVisible.value = false
    loadUsers()
  } finally {
    saving.value = false
  }
}

const assignVisible = ref(false)
const assignUser = ref<any>(null)
const assignRoleIds = ref<number[]>([])
function openAssign(row: any) {
  assignUser.value = row
  assignRoleIds.value = [...(row.roleIds || [])]
  assignVisible.value = true
}
async function saveAssign() {
  saving.value = true
  try {
    await assignUserRoles(assignUser.value.id, assignRoleIds.value)
    Toast.success('角色已更新')
    assignVisible.value = false
    loadUsers()
  } finally {
    saving.value = false
  }
}

// ==================== 角色 ====================
const roleRows = ref<any[]>([])
const permOptions = ref<any[]>([])

/** 按菜单模块分组的权限项; 未匹配分组的归入"其他" */
const permGroups = computed(() => {
  const byCode = new Map(permOptions.value.map((p) => [p.code, p]))
  const used = new Set<string>()
  const groups = PERMISSION_GROUPS.map((g) => {
    const items = g.codes.map((c) => byCode.get(c)).filter(Boolean)
    items.forEach((p: any) => used.add(p.code))
    return { title: g.title, items }
  }).filter((g) => g.items.length > 0)
  const rest = permOptions.value.filter((p) => !used.has(p.code))
  if (rest.length) groups.push({ title: '其他', items: rest })
  return groups.map((g) => ({
    ...g,
    checkedCount: g.items.filter((p: any) => permChecked.value.includes(p.value)).length,
  }))
})
const scopeOptions = [
  { value: 'ALL', label: '全部数据' },
  { value: 'WAREHOUSE', label: '按仓库' },
  { value: 'ORG', label: '本机构' },
  { value: 'SELF', label: '本人' },
]

const roleColumns = [
  { title: '编码', dataIndex: 'code' },
  { title: '名称', dataIndex: 'name' },
  { title: '数据范围', dataIndex: 'dataScope' },
  { title: '权限点数', dataIndex: 'permIds', render: (_: any, r: any) => r.permIds?.length || 0 },
  { title: '备注', dataIndex: 'remark' },
  {
    title: '操作',
    render: (_: any, r: any) => h('div', { style: 'display:flex;gap:6px' }, [
      userStore.hasPermission(PERMISSION.ROLE_MANAGE_EDIT) && h(Button, { size: 'small', theme: 'light', onClick: () => openPerms(r) }, () => '配置权限'),
      userStore.hasPermission(PERMISSION.DATA_SCOPE_MANAGE) && h(Button, { size: 'small', theme: 'light', onClick: () => openScope(r) }, () => '数据权限'),
    ].filter(Boolean)),
  },
]

async function loadRoles() {
  loading.value = true
  try {
    const res: any = await listRoles()
    roleRows.value = res
    roleOptions.value = res.map((r: any) => ({ value: r.id, label: r.name }))
  } finally {
    loading.value = false
  }
}

const roleVisible = ref(false)
const roleForm = reactive({ code: '', name: '', dataScope: 'SELF', remark: '' })
function openCreateRole() {
  Object.assign(roleForm, { code: '', name: '', dataScope: 'SELF', remark: '' })
  roleVisible.value = true
}
async function saveRole() {
  if (!roleForm.code || !roleForm.name) {
    Toast.warning('角色编码/名称必填')
    return
  }
  saving.value = true
  try {
    await createRole(roleForm)
    Toast.success('角色已创建')
    roleVisible.value = false
    loadRoles()
  } finally {
    saving.value = false
  }
}

const permVisible = ref(false)
const permRole = ref<any>(null)
const permChecked = ref<number[]>([])
function openPerms(row: any) {
  permRole.value = row
  permChecked.value = [...(row.permIds || [])]
  permVisible.value = true
}
function togglePerm(id: number) {
  const idx = permChecked.value.indexOf(id)
  if (idx >= 0) permChecked.value.splice(idx, 1)
  else permChecked.value.push(id)
}
/** 组内全选/全不选 */
function toggleGroup(g: any) {
  const ids = g.items.map((p: any) => p.value)
  const allChecked = ids.every((id: number) => permChecked.value.includes(id))
  if (allChecked) {
    permChecked.value = permChecked.value.filter((id) => !ids.includes(id))
  } else {
    permChecked.value = [...new Set([...permChecked.value, ...ids])]
  }
}
async function savePerms() {
  saving.value = true
  try {
    await assignRolePerms(permRole.value.id, permChecked.value)
    Toast.success('权限已更新, 重新登录后生效')
    permVisible.value = false
    loadRoles()
  } finally {
    saving.value = false
  }
}

// ==================== 数据权限 P008 ====================
const scopeVisible = ref(false)
const scopeRole = ref<any>(null)
const scopeForm = reactive({ dataScope: 'ALL', warehouseIds: [] as number[] })
const warehouseOptions = ref<any[]>([])

async function openScope(row: any) {
  scopeRole.value = row
  scopeForm.dataScope = row.dataScope || 'ALL'
  scopeForm.warehouseIds = []
  scopeVisible.value = true
  if (!warehouseOptions.value.length) {
    const whs: any = await listWarehouses()
    warehouseOptions.value = (whs || []).map((w: any) => ({ value: w.id, label: w.name }))
  }
  scopeForm.warehouseIds = [...((await getRoleWarehouses(row.id)) as any || [])]
}

async function saveScope() {
  if (scopeForm.dataScope === 'WAREHOUSE' && !scopeForm.warehouseIds.length) {
    Toast.warning('按仓库模式需至少选择一个仓库')
    return
  }
  saving.value = true
  try {
    await assignDataScope(scopeRole.value.id, { ...scopeForm })
    Toast.success('数据权限已更新, 重新登录后生效')
    scopeVisible.value = false
    loadRoles()
  } finally {
    saving.value = false
  }
}

function switchTab(key: string) {
  tab.value = key
  if (key === 'users') loadUsers()
  if (key === 'roles') loadRoles()
}

onMounted(async () => {
  const perms: any = await listPermissions()
  permOptions.value = perms.map((p: any) => ({ value: p.id, label: p.permName, code: p.permCode }))
  await loadRoles()
  loadUsers()
})
</script>

<style scoped>
.page-card { background: #fff; border-radius: 8px; padding: 16px; }
.seg-tabs { display: flex; gap: 4px; margin-bottom: 14px; border-bottom: 1px solid rgba(17, 24, 39, 0.08); }
.seg-tab { padding: 8px 16px; font-size: 13px; color: #4b5563; cursor: pointer; border-bottom: 2px solid transparent; }
.seg-tab.active { color: #2563eb; border-bottom-color: #2563eb; font-weight: 500; }
.toolbar { display: flex; gap: 8px; margin-bottom: 12px; }
.w200 { width: 200px; }
.form-grid { display: grid; grid-template-columns: 90px 1fr; gap: 10px 12px; align-items: center; }
.form-grid label { font-size: 13px; color: #4b5563; text-align: right; }
.perm-groups { max-height: 480px; overflow: auto; display: flex; flex-direction: column; gap: 4px; }
.perm-group { border: 1px solid rgba(17, 24, 39, 0.07); border-radius: 8px; padding: 10px 12px; }
.perm-group-head {
  display: flex; align-items: center; gap: 8px; cursor: pointer; user-select: none;
  padding-bottom: 8px; margin-bottom: 8px; border-bottom: 1px dashed rgba(17, 24, 39, 0.08);
}
.perm-group-title { font-size: 13px; font-weight: 600; color: #111827; }
.perm-group-count { margin-left: auto; font-size: 12px; color: #9ca3af; font-variant-numeric: tabular-nums; }
.perm-grid { display: grid; grid-template-columns: 1fr 1fr 1fr; gap: 6px 16px; }
.perm-item { display: flex; align-items: center; gap: 6px; font-size: 13px; color: #374151; }
</style>
