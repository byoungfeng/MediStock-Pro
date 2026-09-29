<template>
  <div class="page-card">
    <div class="toolbar">
      <span class="hint">统一待办: 申领 / 采购申请 / 采购订单 / 调拨 / 报废 的待审批单据</span>
      <Button theme="solid" type="primary" :on-click="load">刷新</Button>
    </div>

    <Table :columns="columns" :data-source="rows" row-key="bizId" size="small" :loading="loading"
           :pagination="false" />

    <Modal title="驳回原因" :visible="rejectModal" :footer="noFooter" :on-cancel="() => { rejectModal = false }">
      <Input type="textarea" :value="rejectReason" placeholder="请填写驳回原因 (可选)"
             :on-change="(v: string) => (rejectReason = v)" />
      <div class="modal-actions">
        <Button :on-click="() => { rejectModal = false }">取消</Button>
        <Button theme="solid" type="danger" :on-click="confirmReject">确认驳回</Button>
      </div>
    </Modal>

    <!-- 审批留痕 -->
    <Modal title="审批留痕" :visible="trailModal" :width="560" :footer="noFooter"
           :on-cancel="() => { trailModal = false }">
      <div v-if="trail">
        <p v-if="trail.instance" class="trail-meta">
          状态: {{ trail.instance.status }} ｜ 提交人: #{{ trail.instance.submitterId }} ｜
          提交时间: {{ trail.instance.createdAt }}
        </p>
        <p v-else class="trail-meta">无审批实例 (历史数据或未提交)</p>
        <div v-for="r in trail.records" :key="r.id" class="trail-item">
          <Tag :color="(r.action === 'APPROVE' ? 'green' : 'red') as any">
            {{ r.action === 'APPROVE' ? '同意' : '驳回' }}
          </Tag>
          <span>审批人 #{{ r.approverId }} ｜ {{ r.createdAt }}</span>
          <span v-if="r.comment" class="trail-comment">「{{ r.comment }}」</span>
        </div>
        <p v-if="trail.instance && !trail.records?.length" class="trail-meta">暂无审批记录</p>
      </div>
    </Modal>
  </div>
</template>

<script setup lang="ts">
import { h, onMounted, ref } from 'vue'
import { Button, Input, Modal, Table, Tag, Toast } from '@kousum/semi-ui-vue'
import { getApprovalTodo, approvalAction, getApprovalTrail } from '@/api/approval'
import { PERMISSION } from '@/constants/permissions'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const noFooter: any = null
const loading = ref(false)
const rows = ref<any[]>([])
const rejectModal = ref(false)
const rejectReason = ref('')
const rejectTarget = ref<any>(null)

const typeMap: Record<string, [string, any]> = {
  ISSUE_REQUEST: ['领用申请', 'blue'],
  PURCHASE_REQUEST: ['采购申请', 'cyan'],
  PURCHASE_ORDER: ['采购订单', 'purple'],
  TRANSFER: ['调拨单', 'orange'],
  SCRAP: ['报废单', 'red'],
}

const approvePermMap: Record<string, string> = {
  ISSUE_REQUEST: PERMISSION.ISSUE_REQUEST_APPROVE,
  PURCHASE_REQUEST: PERMISSION.PURCHASE_REQUEST_APPROVE,
  PURCHASE_ORDER: PERMISSION.PURCHASE_ORDER_APPROVE,
  TRANSFER: PERMISSION.STOCK_TRANSFER_APPROVE,
  SCRAP: PERMISSION.STOCK_SCRAP_APPROVE,
}

function canApprove(row: any) {
  const p = approvePermMap[row.bizType]
  return p ? userStore.hasPermission(p) : false
}

const columns = [
  { title: '提交时间', dataIndex: 'createdAt', width: 170 },
  {
    title: '类型', dataIndex: 'bizType', width: 110,
    render: (v: string) => {
      const [text, color] = typeMap[v] || [v, 'grey']
      return h(Tag, { color }, () => text)
    },
  },
  { title: '单号', dataIndex: 'bizNo' },
  { title: '摘要', dataIndex: 'title' },
  { title: '申请人', dataIndex: 'applicantName', width: 110 },
  {
    title: '操作', width: 230,
    render: (_: any, row: any) => [
      canApprove(row) && h(Button, {
        size: 'small', theme: 'solid', type: 'primary', style: { marginRight: '8px' },
        onClick: () => act(row, true),
      }, () => '同意'),
      canApprove(row) && h(Button, {
        size: 'small', theme: 'light', type: 'danger', style: { marginRight: '8px' },
        onClick: () => openReject(row),
      }, () => '驳回'),
      h(Button, { size: 'small', theme: 'light', onClick: () => openTrail(row) }, () => '留痕'),
    ].filter(Boolean),
  },
]

async function load() {
  loading.value = true
  try {
    rows.value = (await getApprovalTodo()) as any[]
  } finally {
    loading.value = false
  }
}

async function act(row: any, pass: boolean, reason?: string) {
  try {
    await approvalAction({ bizType: row.bizType, bizId: row.bizId, pass, reason })
    Toast.success(pass ? '已同意' : '已驳回')
    load()
  } catch {
    // 拦截器已提示 (如 INV_006 状态已变化), 刷新列表
    load()
  }
}

function openReject(row: any) {
  rejectTarget.value = row
  rejectReason.value = ''
  rejectModal.value = true
}

async function confirmReject() {
  rejectModal.value = false
  await act(rejectTarget.value, false, rejectReason.value || undefined)
}

// ==================== 审批留痕 ====================
const trailModal = ref(false)
const trail = ref<any>(null)

async function openTrail(row: any) {
  trail.value = await getApprovalTrail(row.bizType, row.bizId)
  trailModal.value = true
}

onMounted(load)
</script>

<style scoped>
.page-card { background: #fff; border-radius: 8px; padding: 16px; }
.toolbar { display: flex; gap: 12px; margin-bottom: 16px; align-items: center; justify-content: space-between; }
.hint { font-size: 12px; color: #6b7280; }
.modal-actions { display: flex; justify-content: flex-end; gap: 8px; margin-top: 16px; }
.trail-meta { font-size: 12px; color: #6b7280; margin: 0 0 10px; }
.trail-item { display: flex; gap: 8px; align-items: center; padding: 6px 0; border-bottom: 1px dashed #eef1f3; font-size: 13px; }
.trail-comment { color: #4b5563; }
</style>
