<template>
  <div class="page-card">
    <div class="seg-tabs">
      <div v-for="t in tabs" :key="t.key" class="seg-tab" :class="{ active: tab === t.key }"
           @click="switchTab(t.key)">{{ t.text }}</div>
    </div>

    <!-- ==================== 采购申请 ==================== -->
    <template v-if="tab === 'requests'">
      <div class="toolbar">
        <Select class="w180" :value="reqQuery.status" :option-list="reqStatusOptions" placeholder="状态"
                :on-change="(v: any) => { reqQuery.status = v; loadRequests() }" />
        <Button theme="solid" type="primary" :on-click="loadRequests">查询</Button>
        <Button v-permission="PERMISSION.PURCHASE_REQUEST_EDIT" type="primary" theme="light" :on-click="openCreateRequest">新建采购申请</Button>
      </div>
      <Table :columns="reqColumns" :data-source="reqRows" row-key="id" size="small" :loading="loading"
             :pagination="{ currentPage: reqQuery.page, pageSize: reqQuery.size, total: reqTotal, onChange: (p: number) => { reqQuery.page = p; loadRequests() } }" />
    </template>

    <!-- ==================== 采购协议 ==================== -->
    <template v-if="tab === 'agreements'">
      <div class="toolbar">
        <Select class="w180" :value="agrQuery.status" :option-list="agrStatusOptions" placeholder="状态"
                :on-change="(v: any) => { agrQuery.status = v; loadAgreements() }" />
        <Button theme="solid" type="primary" :on-click="loadAgreements">查询</Button>
        <Button v-permission="PERMISSION.PURCHASE_AGREEMENT_EDIT" type="primary" theme="light" :on-click="openCreateAgreement">新建采购协议</Button>
      </div>
      <Table :columns="agrColumns" :data-source="agrRows" row-key="id" size="small" :loading="loading"
             :pagination="{ currentPage: agrQuery.page, pageSize: agrQuery.size, total: agrTotal, onChange: (p: number) => { agrQuery.page = p; loadAgreements() } }" />
    </template>

    <!-- ==================== 采购订单 ==================== -->
    <template v-if="tab === 'orders'">
      <div class="toolbar">
        <Select class="w180" :value="orderQuery.status" :option-list="orderStatusOptions" placeholder="状态"
                :on-change="(v: any) => { orderQuery.status = v; loadOrders() }" />
        <Button theme="solid" type="primary" :on-click="loadOrders">查询</Button>
        <Button v-permission="PERMISSION.PURCHASE_ORDER_CREATE" type="primary" theme="light" :on-click="openCreateOrder">新建采购订单</Button>
      </div>
      <Table :columns="orderColumns" :data-source="orderRows" row-key="id" size="small" :loading="loading"
             :pagination="{ currentPage: orderQuery.page, pageSize: orderQuery.size, total: orderTotal, onChange: (p: number) => { orderQuery.page = p; loadOrders() } }" />
    </template>

    <!-- ==================== 收货单 ==================== -->
    <template v-if="tab === 'receipts'">
      <div class="toolbar">
        <Select class="w180" :value="receiptQuery.status" :option-list="receiptStatusOptions" placeholder="状态"
                :on-change="(v: any) => { receiptQuery.status = v; loadReceipts() }" />
        <Button theme="solid" type="primary" :on-click="loadReceipts">查询</Button>
      </div>
      <Table :columns="receiptColumns" :data-source="receiptRows" row-key="id" size="small" :loading="loading"
             :pagination="{ currentPage: receiptQuery.page, pageSize: receiptQuery.size, total: receiptTotal, onChange: (p: number) => { receiptQuery.page = p; loadReceipts() } }" />
    </template>

    <!-- ==================== 验收单 ==================== -->
    <template v-if="tab === 'acceptances'">
      <div class="toolbar">
        <Select class="w180" :value="accQuery.status" :option-list="accStatusOptions" placeholder="状态"
                :on-change="(v: any) => { accQuery.status = v; loadAcceptances() }" />
        <Button theme="solid" type="primary" :on-click="loadAcceptances">查询</Button>
      </div>
      <Table :columns="accColumns" :data-source="accRows" row-key="id" size="small" :loading="loading"
             :pagination="{ currentPage: accQuery.page, pageSize: accQuery.size, total: accTotal, onChange: (p: number) => { accQuery.page = p; loadAcceptances() } }" />
    </template>

    <!-- 新建采购申请 -->
    <Modal :visible="reqCreateVisible" title="新建采购申请" :width="760"
           :on-ok="saveRequest" :on-cancel="() => { reqCreateVisible = false }" :confirm-loading="saving">
      <div class="form-row">
        <label>申请科室ID *</label>
        <Input :value="String(reqForm.departmentId ?? '')" style="width: 120px"
               :on-change="(v: string) => (reqForm.departmentId = Number(v) || null)" />
        <label>收货仓库 *</label>
        <Select :value="reqForm.warehouseId" :option-list="warehouseOptions" style="width: 160px"
                :on-change="(v: any) => (reqForm.warehouseId = v)" />
      </div>
      <div class="form-row">
        <label>用途说明</label>
        <Input :value="reqForm.purpose" :on-change="(v: string) => (reqForm.purpose = v)" style="width: 480px" />
      </div>
      <div class="line-header">
        <span>申请明细</span>
        <Button size="small" theme="light" :on-click="addReqLine">+ 添加行</Button>
      </div>
      <div class="line-grid line-grid-head">
        <span>物资 *</span><span>申请数量 *</span><span></span>
      </div>
      <div v-for="(line, idx) in reqForm.items" :key="idx" class="line-grid3">
        <Select :value="line.materialId" :option-list="materialOptions" filter
                :on-change="(v: any) => (line.materialId = v)" />
        <Input :value="String(line.qty)" :on-change="(v: string) => (line.qty = Number(v) || 0)" />
        <Button size="small" theme="borderless" type="danger" :on-click="() => reqForm.items.splice(idx, 1)">删</Button>
      </div>
    </Modal>

    <!-- 采购申请详情 + 核定/转单 -->
    <Modal :visible="reqDetailVisible" title="采购申请详情" :width="820" :footer="noFooter"
           :on-cancel="() => { reqDetailVisible = false }">
      <div v-if="reqDetail" class="detail">
        <p>
          单号: {{ reqDetail.master.requestNo }} ｜ 状态: {{ reqStatusText(reqDetail.master.status) }} ｜
          科室: #{{ reqDetail.master.departmentId }} ｜ 收货仓: {{ warehouseName(reqDetail.master.warehouseId) }} ｜
          用途: {{ reqDetail.master.purpose || '-' }}
        </p>
        <div class="line-grid line-grid-head" v-if="reqDetail.master.status === 'PENDING'">
          <span>物资</span><span>申请量</span><span>核定量 *</span>
        </div>
        <div v-if="reqDetail.master.status === 'PENDING'">
          <div v-for="item in reqDetail.items" :key="item.id" class="line-grid3b">
            <span>{{ materialName(item.materialId) }}</span>
            <span>{{ item.qty }}</span>
            <Input :value="String(approveQtys[item.id] ?? item.qty)"
                   :on-change="(v: string) => (approveQtys[item.id] = Number(v) || 0)" />
          </div>
        </div>
        <Table v-else :columns="reqDetailColumns" :data-source="reqDetail.items" row-key="id" size="small" :pagination="false" />

        <!-- 转采购订单 (APPROVED) -->
        <div v-if="reqDetail.master.status === 'APPROVED'" class="receive-box">
          <div class="line-header"><span>转采购订单 (按核定量生成 DRAFT 订单)</span></div>
          <div class="form-row">
            <label>供应商 *</label>
            <Select :value="toOrderForm.supplierId" :option-list="supplierOptions" style="width: 220px" filter
                    :on-change="(v: any) => (toOrderForm.supplierId = v)" />
            <label>期望到货</label>
            <Input :value="toOrderForm.expectDate" :on-change="(v: string) => (toOrderForm.expectDate = v)"
                   style="width: 140px" placeholder="YYYY-MM-DD" />
          </div>
          <div class="line-grid line-grid-head">
            <span>物资</span><span>核定量</span><span>单价(元)</span>
          </div>
          <div v-for="item in reqDetail.items.filter((i: any) => (i.approvedQty ?? i.qty) > 0)" :key="item.id" class="line-grid3b">
            <span>{{ materialName(item.materialId) }}</span>
            <span>{{ item.approvedQty ?? item.qty }}</span>
            <Input :value="String(toOrderForm.prices[item.materialId] ?? 0)"
                   :on-change="(v: string) => (toOrderForm.prices[item.materialId] = Number(v) || 0)" />
          </div>
        </div>

        <div class="detail-actions">
          <template v-if="reqDetail.master.status === 'DRAFT'">
            <Button v-permission="PERMISSION.PURCHASE_REQUEST_EDIT" theme="solid" type="primary" :loading="saving" :on-click="() => reqAction('submit')">提交审批</Button>
            <Button v-permission="PERMISSION.PURCHASE_REQUEST_EDIT" type="danger" theme="light" :on-click="() => reqAction('cancel')">取消申请</Button>
          </template>
          <template v-if="reqDetail.master.status === 'PENDING'">
            <Button v-permission="PERMISSION.PURCHASE_REQUEST_APPROVE" theme="solid" type="primary" :loading="saving" :on-click="() => doReqApprove(true)">审批通过(写入核定)</Button>
            <Button v-permission="PERMISSION.PURCHASE_REQUEST_APPROVE" type="danger" theme="light" :loading="saving" :on-click="() => doReqApprove(false)">驳回</Button>
          </template>
          <Button v-if="reqDetail.master.status === 'APPROVED'" v-permission="PERMISSION.PURCHASE_ORDER_CREATE"
                  theme="solid" type="primary" :loading="saving" :on-click="doToOrder">转采购订单</Button>
        </div>
      </div>
    </Modal>

    <!-- 新建采购协议 -->
    <Modal :visible="agrCreateVisible" title="新建采购协议" :width="760"
           :on-ok="saveAgreement" :on-cancel="() => { agrCreateVisible = false }" :confirm-loading="saving">
      <div class="form-row">
        <label>供应商 *</label>
        <Select :value="agrForm.supplierId" :option-list="supplierOptions" style="width: 220px" filter
                :on-change="(v: any) => (agrForm.supplierId = v)" />
        <label>生效 *</label>
        <Input :value="agrForm.startDate" style="width: 130px" placeholder="YYYY-MM-DD"
               :on-change="(v: string) => (agrForm.startDate = v)" />
        <label>截止 *</label>
        <Input :value="agrForm.endDate" style="width: 130px" placeholder="YYYY-MM-DD"
               :on-change="(v: string) => (agrForm.endDate = v)" />
      </div>
      <div class="form-row">
        <label>付款条件</label>
        <Input :value="agrForm.payTerms" :on-change="(v: string) => (agrForm.payTerms = v)" style="width: 200px"
               placeholder="如: 月结30天" />
        <label>备注</label>
        <Input :value="agrForm.remark" :on-change="(v: string) => (agrForm.remark = v)" style="width: 260px" />
      </div>
      <div class="line-header">
        <span>协议价目</span>
        <Button size="small" theme="light" :on-click="addAgrLine">+ 添加行</Button>
      </div>
      <div class="line-grid line-grid-head">
        <span>物资 *</span><span>协议价(元) *</span><span>税率%</span><span></span>
      </div>
      <div v-for="(line, idx) in agrForm.items" :key="idx" class="line-grid4b">
        <Select :value="line.materialId" :option-list="materialOptions" filter
                :on-change="(v: any) => (line.materialId = v)" />
        <Input :value="String(line.price)" :on-change="(v: string) => (line.price = Number(v) || 0)" />
        <Input :value="String(line.taxRate)" :on-change="(v: string) => (line.taxRate = Number(v) || 0)" />
        <Button size="small" theme="borderless" type="danger" :on-click="() => agrForm.items.splice(idx, 1)">删</Button>
      </div>
    </Modal>

    <!-- 协议详情 -->
    <Modal :visible="agrDetailVisible" title="采购协议详情" :width="760" :footer="noFooter"
           :on-cancel="() => { agrDetailVisible = false }">
      <div v-if="agrDetail" class="detail">
        <p>
          编号: {{ agrDetail.master.agreementNo }} ｜ 状态: {{ agrStatusText(agrDetail.master.status) }} ｜
          供应商: {{ supplierName(agrDetail.master.supplierId) }} ｜
          有效期: {{ agrDetail.master.startDate }} ~ {{ agrDetail.master.endDate }} ｜
          付款: {{ agrDetail.master.payTerms || '-' }}
        </p>
        <Table :columns="agrDetailColumns" :data-source="agrDetail.items" row-key="id" size="small" :pagination="false" />
        <div class="detail-actions">
          <Button v-if="agrDetail.master.status === 'DRAFT'" v-permission="PERMISSION.PURCHASE_AGREEMENT_EDIT"
                  theme="solid" type="primary" :loading="saving" :on-click="() => agrAction('activate')">生效</Button>
          <Button v-if="agrDetail.master.status === 'EFFECTIVE'" v-permission="PERMISSION.PURCHASE_AGREEMENT_EDIT"
                  theme="solid" type="danger" :loading="saving" :on-click="() => agrAction('terminate')">终止协议</Button>
        </div>
      </div>
    </Modal>

    <!-- 新建采购订单 -->
    <Modal :visible="createVisible" title="新建采购订单" :width="860"
           :on-ok="saveOrder" :on-cancel="() => { createVisible  = false }" :confirm-loading="saving">
      <div class="form-row">
        <label>供应商 *</label>
        <Select :value="orderForm.supplierId" :option-list="supplierOptions" style="width: 220px" filter
                :on-change="(v: any) => onOrderSupplierChange(v)" />
        <label>采购协议</label>
        <Select :value="orderForm.agreementId" :option-list="orderAgreementOptions" style="width: 200px"
                placeholder="选协议自动带价" :on-change="(v: any) => onOrderAgreementChange(v)" />
        <label>收货仓库 *</label>
        <Select :value="orderForm.warehouseId" :option-list="warehouseOptions" style="width: 160px"
                :on-change="(v: any) => (orderForm.warehouseId = v)" />
        <label>期望到货</label>
        <Input :value="orderForm.expectDate" :on-change="(v: string) => (orderForm.expectDate = v)"
               style="width: 140px" placeholder="YYYY-MM-DD" />
      </div>
      <div class="form-row">
        <label>备注</label>
        <Input :value="orderForm.remark" :on-change="(v: string) => (orderForm.remark = v)" style="width: 480px" />
      </div>
      <div class="line-header">
        <span>采购明细</span>
        <Button size="small" theme="light" :on-click="addOrderLine">+ 添加行</Button>
      </div>
      <div class="line-grid line-grid-head">
        <span>物资 *</span><span>数量 *</span><span>单价(元) *</span><span>金额</span><span></span>
      </div>
      <div v-for="(line, idx) in orderForm.items" :key="idx" class="line-grid5">
        <Select :value="line.materialId" :option-list="materialOptions" filter
                :on-change="(v: any) => (line.materialId = v)" />
        <Input :value="String(line.orderedQty)" :on-change="(v: string) => (line.orderedQty = Number(v) || 0)" />
        <Input :value="String(line.unitPrice)" :on-change="(v: string) => (line.unitPrice = Number(v) || 0)" />
        <span class="amount">{{ (line.orderedQty * line.unitPrice).toFixed(2) }}</span>
        <Button size="small" theme="borderless" type="danger" :on-click="() => orderForm.items.splice(idx, 1)">删</Button>
      </div>
      <div class="total-row">合计: {{ orderTotalQty }} 件 / ¥{{ orderTotalAmount.toFixed(2) }}</div>
    </Modal>

    <!-- 订单详情 + 收货登记 -->
    <Modal :visible="orderDetailVisible" title="采购订单详情" :width="880" :footer="noFooter"
           :on-cancel="() => { orderDetailVisible  = false }">
      <div v-if="orderDetail" class="detail">
        <p>
          单号: {{ orderDetail.master.orderNo }} ｜ 状态: {{ orderStatusText(orderDetail.master.status) }} ｜
          供应商: {{ supplierName(orderDetail.master.supplierId) }} ｜ 收货仓: {{ warehouseName(orderDetail.master.warehouseId) }} ｜
          总额: ¥{{ orderDetail.master.totalAmount }}
        </p>
        <Table :columns="orderDetailColumns" :data-source="orderDetail.items" row-key="id" size="small" :pagination="false" />

        <!-- 收货登记 (APPROVED/RECEIVING) -->
        <div v-if="['APPROVED', 'RECEIVING'].includes(orderDetail.master.status)" class="receive-box">
          <div class="line-header"><span>到货登记 (批次/效期/数量; 实收不得超订)</span></div>
          <div class="line-grid line-grid-head">
            <span>物资</span><span>批号 *</span><span>效期</span><span>数量 *</span>
          </div>
          <div v-for="(line, idx) in receiptForm.items" :key="idx" class="line-grid4">
            <span>{{ materialName(line.materialId) }} (剩 {{ remainingOf(line.materialId) }})</span>
            <Input :value="line.batchNo" :on-change="(v: string) => (line.batchNo = v)" placeholder="供应商批号" />
            <Input :value="line.expiryDate" :on-change="(v: string) => (line.expiryDate = v)" placeholder="YYYY-MM-DD" />
            <Input :value="String(line.qty)" :on-change="(v: string) => (line.qty = Number(v) || 0)" />
          </div>
          <div class="form-row">
            <label>到货日期</label>
            <Input :value="receiptForm.arrivalDate" :on-change="(v: string) => (receiptForm.arrivalDate = v)"
                   style="width: 140px" placeholder="YYYY-MM-DD" />
            <label>运输单号</label>
            <Input :value="receiptForm.transportNo" :on-change="(v: string) => (receiptForm.transportNo = v)"
                   style="width: 160px" />
          </div>
        </div>

        <div class="detail-actions">
          <template v-if="orderDetail.master.status === 'DRAFT'">
            <Button v-permission="PERMISSION.PURCHASE_ORDER_CREATE" theme="solid" type="primary" :loading="saving" :on-click="() => orderAction('submit')">提交审批</Button>
            <Button v-permission="PERMISSION.PURCHASE_ORDER_CREATE" type="danger" theme="light" :on-click="() => orderAction('cancel')">取消订单</Button>
          </template>
          <template v-if="orderDetail.master.status === 'PENDING'">
            <Button v-permission="PERMISSION.PURCHASE_ORDER_APPROVE" theme="solid" type="primary" :loading="saving" :on-click="() => orderAction('approve')">审批通过</Button>
            <Button v-permission="PERMISSION.PURCHASE_ORDER_APPROVE" type="danger" theme="light" :on-click="() => orderAction('cancel')">取消订单</Button>
          </template>
          <Button v-if="['APPROVED', 'RECEIVING'].includes(orderDetail.master.status)" v-permission="PERMISSION.PURCHASE_RECEIPT_REGISTER"
                  theme="solid" type="primary" :loading="saving" :on-click="doRegisterReceipt">登记到货</Button>
        </div>
      </div>
    </Modal>

    <!-- 收货单详情 -->
    <Modal :visible="receiptDetailVisible" title="收货单详情" :width="760" :footer="noFooter"
           :on-cancel="() => { receiptDetailVisible  = false }">
      <div v-if="receiptDetail" class="detail">
        <p>
          单号: {{ receiptDetail.master.receiptNo }} ｜ 状态: {{ receiptStatusText(receiptDetail.master.status) }} ｜
          到货日期: {{ receiptDetail.master.arrivalDate }} ｜ 运输单号: {{ receiptDetail.master.transportNo || '-' }}
        </p>
        <Table :columns="receiptDetailColumns" :data-source="receiptDetail.items" row-key="id" size="small" :pagination="false" />
        <div class="detail-actions" v-if="receiptDetail.master.status === 'REGISTERED'">
          <Button v-permission="PERMISSION.PURCHASE_ACCEPTANCE_CREATE" theme="solid" type="primary" :loading="saving" :on-click="doCreateAcceptance">生成验收单</Button>
        </div>
      </div>
    </Modal>

    <!-- 验收单详情 + 验收录入 -->
    <Modal :visible="accDetailVisible" title="验收单详情" :width="880" :footer="noFooter"
           :on-cancel="() => { accDetailVisible  = false }">
      <div v-if="accDetail" class="detail">
        <p>
          单号: {{ accDetail.master.acceptanceNo }} ｜ 状态: {{ accStatusText(accDetail.master.status) }} ｜
          收货单: #{{ accDetail.master.receiptId }}
          <template v-if="accDetail.master.result">｜ 结论: {{ accDetail.master.result }}</template>
        </p>
        <Table :columns="accDetailColumns" :data-source="accDetail.items" row-key="id" size="small" :pagination="false" />

        <div v-if="accDetail.master.status === 'PENDING'" class="receive-box">
          <div class="line-header"><span>验收录入 (合格+拒收=实收; 拒收必填原因)</span></div>
          <div class="line-grid line-grid-head">
            <span>物资/批次</span><span>实收</span><span>合格 *</span><span>拒收</span><span>拒收原因</span>
          </div>
          <div v-for="item in accDetail.items" :key="item.id" class="line-grid5b">
            <span>{{ materialName(item.materialId) }} / {{ item.batchNo }}</span>
            <span>{{ item.receivedQty }}</span>
            <Input :value="String(accForm[item.id]?.acceptedQty ?? item.receivedQty)"
                   :on-change="(v: string) => setAcc(item.id, 'acceptedQty', Number(v) || 0)" />
            <Input :value="String(accForm[item.id]?.rejectedQty ?? 0)"
                   :on-change="(v: string) => setAcc(item.id, 'rejectedQty', Number(v) || 0)" />
            <Input :value="accForm[item.id]?.rejectReason ?? ''"
                   :on-change="(v: string) => setAcc(item.id, 'rejectReason', v)" placeholder="拒收时必填" />
          </div>
        </div>

        <div class="detail-actions" v-if="accDetail.master.status === 'PENDING'">
          <Button v-permission="PERMISSION.PURCHASE_ACCEPTANCE_EXECUTE" theme="solid" type="primary" :loading="saving" :on-click="() => doAct(true)">验收通过(生成入库单)</Button>
          <Button v-permission="PERMISSION.PURCHASE_ACCEPTANCE_EXECUTE" type="danger" theme="light" :loading="saving" :on-click="() => doAct(false)">整单拒收</Button>
        </div>
      </div>
    </Modal>

    <!-- 验收异常 P025 -->
    <Modal :visible="exVisible" :title="`验收异常 - ${exAcceptance?.acceptanceNo || ''}`" :width="900" :footer="noFooter"
           :on-cancel="() => { exVisible = false }">
      <div class="ex-form">
        <Select :value="exForm.type" :option-list="exTypeOptions" style="width: 130px"
                :on-change="(v: any) => (exForm.type = v)" />
        <Input :value="exForm.reason" :on-change="(v: string) => (exForm.reason = v)"
               placeholder="异常原因 *" style="flex: 1" />
        <Input :value="exForm.responsibility" :on-change="(v: string) => (exForm.responsibility = v)"
               placeholder="责任方" style="width: 140px" />
        <Button v-permission="PERMISSION.ACCEPTANCE_EXCEPTION" theme="solid" type="primary" :loading="saving" :on-click="doCreateEx">登记异常</Button>
      </div>
      <Table :columns="exColumns" :data-source="exRows" row-key="id" size="small" :pagination="false" />
    </Modal>

    <!-- 关闭异常 -->
    <Modal :visible="resolveVisible" title="关闭异常" :width="440"
           :on-ok="doResolveEx" :on-cancel="() => { resolveVisible = false }" :confirm-loading="saving">
      <Input :value="resolveResult" :on-change="(v: string) => (resolveResult = v)"
             placeholder="处理结果 *" type="textarea" />
    </Modal>
  </div>
</template>

<script setup lang="ts">
const noFooter: any = null // Semi Modal footer 类型不收 null, 运行时需要
import { computed, h, onMounted, reactive, ref } from 'vue'
import { Button, Input, Modal, Select, Table, Tag, Toast } from '@kousum/semi-ui-vue'
import {
  actAcceptance, createAcceptanceFromReceipt, approvePurchaseOrder, cancelPurchaseOrder,
  createAcceptanceException, createPurchaseOrder, getAcceptance, getPurchaseOrder, getReceipt,
  handleAcceptanceException, pageAcceptanceExceptions, pageAcceptances,
  pagePurchaseOrders, pageReceipts, registerReceipt, submitPurchaseOrder,
  pagePurchaseRequests, getPurchaseRequest, createPurchaseRequest, submitPurchaseRequest,
  approvePurchaseRequest, cancelPurchaseRequest, requestToOrder,
  pageAgreements, getAgreement, createAgreement, activateAgreement, terminateAgreement,
} from '@/api/purchase'
import { getAllSuppliers } from '@/api/supplier'
import { pageMaterials } from '@/api/material'
import { getWarehouseTree } from '@/api/warehouse'
import { numCol, moneyCol } from '@/utils/format'
import { PERMISSION } from '@/constants/permissions'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()

const loading = ref(false)
const saving = ref(false)
const tab = ref('requests')
const tabs = [
  { key: 'requests', text: '采购申请' },
  { key: 'orders', text: '采购订单' },
  { key: 'agreements', text: '采购协议' },
  { key: 'receipts', text: '收货登记' },
  { key: 'acceptances', text: '验收管理' },
]

const supplierOptions = ref<any[]>([])
const warehouseOptions = ref<any[]>([])
const materialOptions = ref<any[]>([])

function supplierName(id: number) {
  return supplierOptions.value.find((s) => s.value === id)?.label || id
}
function warehouseName(id: number) {
  return warehouseOptions.value.find((w) => w.value === id)?.label || id
}
function materialName(id: number) {
  return materialOptions.value.find((m) => m.value === id)?.label || id
}

// ==================== 采购申请 ====================
const reqRows = ref<any[]>([])
const reqTotal = ref(0)
const reqQuery = reactive({ page: 1, size: 20, status: '' })
const reqStatusOptions = [
  { value: '', label: '全部状态' },
  { value: 'DRAFT', label: '草稿' },
  { value: 'PENDING', label: '待审批' },
  { value: 'APPROVED', label: '已审批' },
  { value: 'REJECTED', label: '已驳回' },
  { value: 'ORDERED', label: '已转订单' },
  { value: 'CANCELLED', label: '已取消' },
]
function reqStatusText(s: string) {
  return reqStatusOptions.find((o) => o.value === s)?.label || s
}
function reqStatusColor(s: string) {
  return { DRAFT: 'grey', PENDING: 'blue', APPROVED: 'green', REJECTED: 'red', ORDERED: 'violet', CANCELLED: 'grey' }[s] || 'grey'
}
const reqColumns = [
  { title: '申请单号', dataIndex: 'requestNo' },
  { title: '科室', dataIndex: 'departmentId', render: (_: any, r: any) => `#${r.departmentId}` },
  { title: '收货仓', dataIndex: 'warehouseId', render: (_: any, r: any) => warehouseName(r.warehouseId) },
  { title: '用途', dataIndex: 'purpose' },
  { title: '创建时间', dataIndex: 'createdAt' },
  {
    title: '状态', dataIndex: 'status',
    render: (_: any, r: any) => h(Tag, { color: reqStatusColor(r.status) as any }, () => reqStatusText(r.status)),
  },
  {
    title: '操作',
    render: (_: any, r: any) => h(Button, { size: 'small', theme: 'light', onClick: () => openReqDetail(r.id) }, () => '详情'),
  },
]
const reqDetailColumns = [
  { title: '物资', dataIndex: 'materialId', render: (_: any, r: any) => materialName(r.materialId) },
  numCol('申请量', 'qty'),
  { title: '核定量', dataIndex: 'approvedQty', render: (_: any, r: any) => r.approvedQty ?? '-' },
]

async function loadRequests() {
  loading.value = true
  try {
    const res: any = await pagePurchaseRequests({ ...reqQuery })
    reqRows.value = res.records
    reqTotal.value = res.total
  } finally {
    loading.value = false
  }
}

const reqCreateVisible = ref(false)
const reqForm = reactive<any>({ departmentId: null, warehouseId: null, purpose: '', items: [] as any[] })
function openCreateRequest() {
  Object.assign(reqForm, { departmentId: null, warehouseId: null, purpose: '', items: [{ materialId: null, qty: 1 }] })
  reqCreateVisible.value = true
}
function addReqLine() {
  reqForm.items.push({ materialId: null, qty: 1 })
}
async function saveRequest() {
  if (!reqForm.departmentId || !reqForm.warehouseId) {
    Toast.warning('科室ID与收货仓库必填')
    return
  }
  if (reqForm.items.some((l: any) => !l.materialId || l.qty <= 0)) {
    Toast.warning('明细行需完整: 物资/数量>0')
    return
  }
  saving.value = true
  try {
    await createPurchaseRequest({ ...reqForm })
    Toast.success('采购申请已创建(草稿)')
    reqCreateVisible.value = false
    loadRequests()
  } finally {
    saving.value = false
  }
}

const reqDetailVisible = ref(false)
const reqDetail = ref<any>(null)
const approveQtys = reactive<Record<number, number>>({})
const toOrderForm = reactive<any>({ supplierId: null, expectDate: '', prices: {} as Record<number, number> })
async function openReqDetail(id: number) {
  reqDetail.value = await getPurchaseRequest(id)
  Object.keys(approveQtys).forEach((k) => delete approveQtys[Number(k)])
  for (const item of reqDetail.value.items) {
    approveQtys[item.id] = item.approvedQty ?? item.qty
  }
  Object.assign(toOrderForm, { supplierId: null, expectDate: '', prices: {} })
  reqDetailVisible.value = true
}
async function reqAction(kind: 'submit' | 'cancel') {
  saving.value = true
  try {
    const m = reqDetail.value.master
    if (kind === 'submit') await submitPurchaseRequest(m.id, m.version)
    if (kind === 'cancel') await cancelPurchaseRequest(m.id, m.version)
    Toast.success('操作成功')
    reqDetailVisible.value = false
    loadRequests()
  } finally {
    saving.value = false
  }
}
async function doReqApprove(pass: boolean) {
  saving.value = true
  try {
    const m = reqDetail.value.master
    await approvePurchaseRequest(m.id, { version: m.version, pass, approvedQtys: pass ? { ...approveQtys } : null })
    Toast.success(pass ? '已审批通过' : '已驳回')
    reqDetailVisible.value = false
    loadRequests()
  } finally {
    saving.value = false
  }
}
async function doToOrder() {
  if (!toOrderForm.supplierId) {
    Toast.warning('请选择供应商')
    return
  }
  saving.value = true
  try {
    const m = reqDetail.value.master
    const res: any = await requestToOrder(m.id, {
      version: m.version,
      supplierId: toOrderForm.supplierId,
      expectDate: toOrderForm.expectDate || null,
      prices: { ...toOrderForm.prices },
    })
    Toast.success(`已生成采购订单 ${res.orderNo}(草稿), 请到「采购订单」提交审批`)
    reqDetailVisible.value = false
    loadRequests()
  } finally {
    saving.value = false
  }
}

// ==================== 采购协议 ====================
const agrRows = ref<any[]>([])
const agrTotal = ref(0)
const agrQuery = reactive({ page: 1, size: 20, status: '' })
const agrStatusOptions = [
  { value: '', label: '全部状态' },
  { value: 'DRAFT', label: '草稿' },
  { value: 'EFFECTIVE', label: '生效中' },
  { value: 'TERMINATED', label: '已终止' },
]
function agrStatusText(s: string) {
  return agrStatusOptions.find((o) => o.value === s)?.label || s
}
const agrColumns = [
  { title: '协议编号', dataIndex: 'agreementNo' },
  { title: '供应商', dataIndex: 'supplierId', render: (_: any, r: any) => supplierName(r.supplierId) },
  { title: '生效', dataIndex: 'startDate' },
  { title: '截止', dataIndex: 'endDate' },
  { title: '付款条件', dataIndex: 'payTerms' },
  {
    title: '状态', dataIndex: 'status',
    render: (_: any, r: any) => h(Tag, { color: (({ DRAFT: 'grey', EFFECTIVE: 'green', TERMINATED: 'red' } as Record<string, string>)[r.status]) as any }, () => agrStatusText(r.status)),
  },
  {
    title: '操作',
    render: (_: any, r: any) => h(Button, { size: 'small', theme: 'light', onClick: () => openAgrDetail(r.id) }, () => '详情'),
  },
]
const agrDetailColumns = [
  { title: '物资', dataIndex: 'materialId', render: (_: any, r: any) => materialName(r.materialId) },
  moneyCol('协议价', 'price'),
  { title: '税率%', dataIndex: 'taxRate' },
]
async function loadAgreements() {
  loading.value = true
  try {
    const res: any = await pageAgreements({ ...agrQuery })
    agrRows.value = res.records
    agrTotal.value = res.total
  } finally {
    loading.value = false
  }
}
const agrCreateVisible = ref(false)
const agrForm = reactive<any>({ supplierId: null, startDate: '', endDate: '', payTerms: '', remark: '', items: [] as any[] })
function openCreateAgreement() {
  Object.assign(agrForm, { supplierId: null, startDate: '', endDate: '', payTerms: '', remark: '', items: [{ materialId: null, price: 0, taxRate: 13 }] })
  agrCreateVisible.value = true
}
function addAgrLine() {
  agrForm.items.push({ materialId: null, price: 0, taxRate: 13 })
}
async function saveAgreement() {
  if (!agrForm.supplierId || !agrForm.startDate || !agrForm.endDate) {
    Toast.warning('供应商与起止日期必填')
    return
  }
  if (agrForm.items.some((l: any) => !l.materialId || l.price < 0)) {
    Toast.warning('明细行需完整: 物资/协议价>=0')
    return
  }
  saving.value = true
  try {
    await createAgreement({ ...agrForm })
    Toast.success('采购协议已创建(草稿), 详情中点击生效')
    agrCreateVisible.value = false
    loadAgreements()
  } finally {
    saving.value = false
  }
}
const agrDetailVisible = ref(false)
const agrDetail = ref<any>(null)
async function openAgrDetail(id: number) {
  agrDetail.value = await getAgreement(id)
  agrDetailVisible.value = true
}
async function agrAction(kind: 'activate' | 'terminate') {
  saving.value = true
  try {
    if (kind === 'activate') await activateAgreement(agrDetail.value.master.id)
    if (kind === 'terminate') await terminateAgreement(agrDetail.value.master.id)
    Toast.success('操作成功')
    agrDetailVisible.value = false
    loadAgreements()
  } finally {
    saving.value = false
  }
}

// ==================== 订单 ====================
const orderRows = ref<any[]>([])
const orderTotal = ref(0)
const orderQuery = reactive({ page: 1, size: 20, status: '' })
const orderStatusOptions = [
  { value: '', label: '全部状态' },
  { value: 'DRAFT', label: '草稿' },
  { value: 'PENDING', label: '待审批' },
  { value: 'APPROVED', label: '待收货' },
  { value: 'RECEIVING', label: '收货中' },
  { value: 'COMPLETED', label: '已完成' },
  { value: 'CANCELLED', label: '已取消' },
]
function orderStatusText(s: string) {
  return orderStatusOptions.find((o) => o.value === s)?.label || s
}
function orderStatusColor(s: string) {
  return { DRAFT: 'grey', PENDING: 'blue', APPROVED: 'green', RECEIVING: 'violet', COMPLETED: 'green', CANCELLED: 'grey' }[s] || 'grey'
}
const orderColumns = [
  { title: '单号', dataIndex: 'orderNo' },
  { title: '供应商', dataIndex: 'supplierId', render: (_: any, r: any) => supplierName(r.supplierId) },
  { title: '收货仓', dataIndex: 'warehouseId', render: (_: any, r: any) => warehouseName(r.warehouseId) },
  numCol('数量', 'totalQty'),
  moneyCol('金额', 'totalAmount'),
  { title: '期望到货', dataIndex: 'expectDate' },
  {
    title: '状态', dataIndex: 'status',
    render: (_: any, r: any) => h(Tag, { color: orderStatusColor(r.status) as any }, () => orderStatusText(r.status)),
  },
  {
    title: '操作',
    render: (_: any, r: any) => h(Button, { size: 'small', theme: 'light', onClick: () => openOrderDetail(r.id) }, () => '详情'),
  },
]
const orderDetailColumns = [
  { title: '物资', dataIndex: 'materialId', render: (_: any, r: any) => materialName(r.materialId) },
  { title: '订购', dataIndex: 'orderedQty' },
  { title: '已收', dataIndex: 'receivedQty' },
  moneyCol('单价', 'unitPrice'),
  moneyCol('金额', 'amount'),
]

async function loadOrders() {
  loading.value = true
  try {
    const res: any = await pagePurchaseOrders({ ...orderQuery })
    orderRows.value = res.records
    orderTotal.value = res.total
  } finally {
    loading.value = false
  }
}

const createVisible = ref(false)
const orderForm = reactive({ supplierId: undefined as any, warehouseId: undefined as any, agreementId: undefined as any, expectDate: '', remark: '', items: [] as any[] })
const orderAgreementOptions = ref<any[]>([])
const orderTotalQty = computed(() => orderForm.items.reduce((s, l) => s + (l.orderedQty || 0), 0))
const orderTotalAmount = computed(() => orderForm.items.reduce((s, l) => s + (l.orderedQty || 0) * (l.unitPrice || 0), 0))

function openCreateOrder() {
  orderForm.supplierId = undefined
  orderForm.warehouseId = undefined
  orderForm.agreementId = undefined
  orderForm.expectDate = ''
  orderForm.remark = ''
  orderForm.items = [{ materialId: undefined, orderedQty: 1, unitPrice: 0 }]
  orderAgreementOptions.value = []
  createVisible.value = true
}
async function onOrderSupplierChange(v: any) {
  orderForm.supplierId = v
  orderForm.agreementId = undefined
  orderAgreementOptions.value = []
  if (v) {
    const res: any = await pageAgreements({ page: 1, size: 50, supplierId: v, status: 'EFFECTIVE' })
    orderAgreementOptions.value = res.records.map((a: any) => ({ value: a.id, label: `${a.agreementNo} (${a.startDate}~${a.endDate})` }))
  }
}
async function onOrderAgreementChange(v: any) {
  orderForm.agreementId = v
  if (!v) return
  const detail: any = await getAgreement(v)
  const prices = new Map(detail.items.map((i: any) => [i.materialId, Number(i.price)]))
  for (const line of orderForm.items) {
    if (line.materialId && prices.has(line.materialId)) {
      line.unitPrice = prices.get(line.materialId)
    }
  }
  Toast.success('已按协议价填充明细单价')
}
function addOrderLine() {
  orderForm.items.push({ materialId: undefined, orderedQty: 1, unitPrice: 0 })
}
async function saveOrder() {
  if (!orderForm.supplierId || !orderForm.warehouseId) {
    Toast.warning('供应商与收货仓库必填')
    return
  }
  if (orderForm.items.some((l) => !l.materialId || l.orderedQty <= 0 || l.unitPrice < 0)) {
    Toast.warning('明细行需完整: 物资/数量>0/单价>=0')
    return
  }
  saving.value = true
  try {
    await createPurchaseOrder({ ...orderForm, expectDate: orderForm.expectDate || null, agreementId: orderForm.agreementId || null })
    Toast.success('采购订单已创建(草稿)')
    createVisible.value = false
    loadOrders()
  } finally {
    saving.value = false
  }
}

const orderDetailVisible = ref(false)
const orderDetail = ref<any>(null)
const receiptForm = reactive({ arrivalDate: new Date().toISOString().slice(0, 10), transportNo: '', items: [] as any[] })

async function openOrderDetail(id: number) {
  orderDetail.value = await getPurchaseOrder(id)
  receiptForm.items = orderDetail.value.items
    .filter((i: any) => i.receivedQty < i.orderedQty)
    .map((i: any) => ({ materialId: i.materialId, batchNo: '', expiryDate: '', qty: i.orderedQty - i.receivedQty }))
  orderDetailVisible.value = true
}
function remainingOf(materialId: number) {
  const item = orderDetail.value?.items.find((i: any) => i.materialId === materialId)
  return item ? item.orderedQty - item.receivedQty : 0
}
async function orderAction(kind: 'submit' | 'approve' | 'cancel') {
  saving.value = true
  try {
    const m = orderDetail.value.master
    if (kind === 'submit') await submitPurchaseOrder(m.id, m.version)
    if (kind === 'approve') await approvePurchaseOrder(m.id, m.version)
    if (kind === 'cancel') await cancelPurchaseOrder(m.id, m.version)
    Toast.success('操作成功')
    orderDetailVisible.value = false
    loadOrders()
  } finally {
    saving.value = false
  }
}
async function doRegisterReceipt() {
  const lines = receiptForm.items.filter((l) => l.qty > 0)
  if (!lines.length || lines.some((l) => !l.batchNo)) {
    Toast.warning('到货明细需填写批号且数量>0')
    return
  }
  saving.value = true
  try {
    await registerReceipt({
      orderId: orderDetail.value.master.id,
      arrivalDate: receiptForm.arrivalDate,
      transportNo: receiptForm.transportNo,
      items: lines.map((l) => ({ ...l, expiryDate: l.expiryDate || null })),
    })
    Toast.success('到货已登记, 请到「收货登记」生成验收单')
    orderDetailVisible.value = false
    loadOrders()
  } finally {
    saving.value = false
  }
}

// ==================== 收货单 ====================
const receiptRows = ref<any[]>([])
const receiptTotal = ref(0)
const receiptQuery = reactive({ page: 1, size: 20, status: '' })
const receiptStatusOptions = [
  { value: '', label: '全部状态' },
  { value: 'REGISTERED', label: '已登记' },
  { value: 'ACCEPTED', label: '验收通过' },
  { value: 'REJECTED', label: '验收拒收' },
]
function receiptStatusText(s: string) {
  return receiptStatusOptions.find((o) => o.value === s)?.label || s
}
const receiptColumns = [
  { title: '单号', dataIndex: 'receiptNo' },
  { title: '采购订单', dataIndex: 'orderId', render: (_: any, r: any) => `#${r.orderId}` },
  { title: '到货日期', dataIndex: 'arrivalDate' },
  { title: '运输单号', dataIndex: 'transportNo' },
  {
    title: '状态', dataIndex: 'status',
    render: (_: any, r: any) => h(Tag, { color: (({ REGISTERED: 'blue', ACCEPTED: 'green', REJECTED: 'red' } as Record<string, string>)[r.status]) as any }, () => receiptStatusText(r.status)),
  },
  {
    title: '操作',
    render: (_: any, r: any) => h(Button, { size: 'small', theme: 'light', onClick: () => openReceiptDetail(r.id) }, () => '详情'),
  },
]
const receiptDetailColumns = [
  { title: '物资', dataIndex: 'materialId', render: (_: any, r: any) => materialName(r.materialId) },
  { title: '批号', dataIndex: 'batchNo' },
  { title: '效期', dataIndex: 'expiryDate' },
  numCol('数量', 'qty'),
]
async function loadReceipts() {
  loading.value = true
  try {
    const res: any = await pageReceipts({ ...receiptQuery })
    receiptRows.value = res.records
    receiptTotal.value = res.total
  } finally {
    loading.value = false
  }
}
const receiptDetailVisible = ref(false)
const receiptDetail = ref<any>(null)
async function openReceiptDetail(id: number) {
  receiptDetail.value = await getReceipt(id)
  receiptDetailVisible.value = true
}
async function doCreateAcceptance() {
  saving.value = true
  try {
    await createAcceptanceFromReceipt(receiptDetail.value.master.id)
    Toast.success('验收单已生成, 请到「验收管理」录入结果')
    receiptDetailVisible.value = false
    loadReceipts()
  } finally {
    saving.value = false
  }
}

// ==================== 验收单 ====================
const accRows = ref<any[]>([])
const accTotal = ref(0)
const accQuery = reactive({ page: 1, size: 20, status: '' })
const accStatusOptions = [
  { value: '', label: '全部状态' },
  { value: 'PENDING', label: '待验收' },
  { value: 'PASSED', label: '已通过' },
  { value: 'REJECTED', label: '已拒收' },
]
function accStatusText(s: string) {
  return accStatusOptions.find((o) => o.value === s)?.label || s
}
const accColumns = [
  { title: '单号', dataIndex: 'acceptanceNo' },
  { title: '收货单', dataIndex: 'receiptId', render: (_: any, r: any) => `#${r.receiptId}` },
  {
    title: '状态', dataIndex: 'status',
    render: (_: any, r: any) => h(Tag, { color: (({ PENDING: 'blue', PASSED: 'green', REJECTED: 'red' } as Record<string, string>)[r.status]) as any }, () => accStatusText(r.status)),
  },
  { title: '结论', dataIndex: 'result' },
  { title: '创建时间', dataIndex: 'createdAt' },
  {
    title: '操作',
    render: (_: any, r: any) => h('div', { style: 'display:flex;gap:6px' }, [
      h(Button, { size: 'small', theme: 'light', onClick: () => openAccDetail(r.id) }, () => '详情'),
      userStore.hasPermission(PERMISSION.ACCEPTANCE_EXCEPTION) && h(Button, { size: 'small', theme: 'light', onClick: () => openExModal(r) }, () => '异常'),
    ].filter(Boolean)),
  },
]
const accDetailColumns = [
  { title: '物资', dataIndex: 'materialId', render: (_: any, r: any) => materialName(r.materialId) },
  { title: '批号', dataIndex: 'batchNo' },
  { title: '效期', dataIndex: 'expiryDate' },
  { title: '实收', dataIndex: 'receivedQty' },
  { title: '合格', dataIndex: 'acceptedQty' },
  { title: '拒收', dataIndex: 'rejectedQty' },
  { title: '拒收原因', dataIndex: 'rejectReason' },
]
async function loadAcceptances() {
  loading.value = true
  try {
    const res: any = await pageAcceptances({ ...accQuery })
    accRows.value = res.records
    accTotal.value = res.total
  } finally {
    loading.value = false
  }
}
const accDetailVisible = ref(false)
const accDetail = ref<any>(null)
const accForm = reactive<Record<number, any>>({})
async function openAccDetail(id: number) {
  accDetail.value = await getAcceptance(id)
  Object.keys(accForm).forEach((k) => delete accForm[Number(k)])
  for (const item of accDetail.value.items) {
    accForm[item.id] = { acceptedQty: item.receivedQty, rejectedQty: 0, rejectReason: '' }
  }
  accDetailVisible.value = true
}
function setAcc(itemId: number, key: string, val: any) {
  accForm[itemId] = { ...accForm[itemId], [key]: val }
}
async function doAct(pass: boolean) {
  const items = accDetail.value.items.map((item: any) => ({
    itemId: item.id,
    acceptedQty: pass ? (accForm[item.id]?.acceptedQty ?? item.receivedQty) : 0,
    rejectedQty: pass ? (accForm[item.id]?.rejectedQty ?? 0) : item.receivedQty,
    rejectReason: pass ? (accForm[item.id]?.rejectReason || null) : (accForm[item.id]?.rejectReason || '整单拒收'),
  }))
  saving.value = true
  try {
    const res: any = await actAcceptance(accDetail.value.master.id, {
      version: accDetail.value.master.version, pass, remark: pass ? '验收合格' : '验收不合格', items,
    })
    Toast.success(pass ? `验收通过, 入库单已生成(#${res.inboundId}), 请到入库管理确认` : '已整单拒收')
    accDetailVisible.value = false
    loadAcceptances()
  } finally {
    saving.value = false
  }
}

// ==================== 验收异常 P025 ====================
const exVisible = ref(false)
const exAcceptance = ref<any>(null)
const exRows = ref<any[]>([])
const exForm = reactive({ type: 'QUALITY', reason: '', responsibility: '' })
const exTypeOptions = [
  { value: 'QUALITY', label: '质量问题' },
  { value: 'QUANTITY', label: '数量差异' },
  { value: 'PACKAGE', label: '包装破损' },
  { value: 'OTHER', label: '其他' },
]
const exColumns = [
  { title: '异常单号', dataIndex: 'exceptionNo' },
  { title: '类型', dataIndex: 'type', render: (_: any, r: any) => exTypeOptions.find((o) => o.value === r.type)?.label || r.type },
  { title: '原因', dataIndex: 'reason' },
  { title: '责任方', dataIndex: 'responsibility' },
  {
    title: '状态', dataIndex: 'status',
    render: (_: any, r: any) => h(Tag, {
      color: (({ OPEN: 'red', RECTIFYING: 'orange', RESOLVED: 'green' } as Record<string, string>)[r.status]) as any,
    }, () => ({ OPEN: '待处理', RECTIFYING: '整改中', RESOLVED: '已关闭' } as Record<string, string>)[r.status] || r.status),
  },
  { title: '处理结果', dataIndex: 'result' },
  {
    title: '操作',
    render: (_: any, r: any) => h('div', { style: 'display:flex;gap:6px' }, [
      r.status === 'OPEN' && userStore.hasPermission(PERMISSION.ACCEPTANCE_EXCEPTION) ? h(Button, { size: 'small', theme: 'light', onClick: () => doHandleEx(r, 'RECTIFY') }, () => '开始整改') : null,
      r.status !== 'RESOLVED' && userStore.hasPermission(PERMISSION.ACCEPTANCE_EXCEPTION) ? h(Button, { size: 'small', theme: 'light', type: 'primary', onClick: () => openResolveEx(r) }, () => '关闭') : null,
    ].filter(Boolean)),
  },
]

async function openExModal(row: any) {
  exAcceptance.value = row
  exForm.type = 'QUALITY'
  exForm.reason = ''
  exForm.responsibility = ''
  exVisible.value = true
  await loadExceptions()
}

async function loadExceptions() {
  const res: any = await pageAcceptanceExceptions({ page: 1, size: 50, acceptanceId: exAcceptance.value.id })
  exRows.value = res.records
}

async function doCreateEx() {
  if (!exForm.reason) {
    Toast.warning('异常原因必填')
    return
  }
  saving.value = true
  try {
    await createAcceptanceException({ acceptanceId: exAcceptance.value.id, ...exForm })
    Toast.success('异常已登记')
    exForm.reason = ''
    exForm.responsibility = ''
    await loadExceptions()
  } finally {
    saving.value = false
  }
}

async function doHandleEx(row: any, action: string, result?: string) {
  saving.value = true
  try {
    await handleAcceptanceException(row.id, { action, result })
    Toast.success(action === 'RECTIFY' ? '已开始整改' : '异常已关闭')
    await loadExceptions()
  } finally {
    saving.value = false
  }
}

const resolveVisible = ref(false)
const resolveTarget = ref<any>(null)
const resolveResult = ref('')
function openResolveEx(row: any) {
  resolveTarget.value = row
  resolveResult.value = ''
  resolveVisible.value = true
}
async function doResolveEx() {
  if (!resolveResult.value) {
    Toast.warning('关闭异常必须填写处理结果')
    return
  }
  await doHandleEx(resolveTarget.value, 'RESOLVE', resolveResult.value)
  resolveVisible.value = false
}

function switchTab(key: string) {
  tab.value = key
  if (key === 'requests') loadRequests()
  if (key === 'orders') loadOrders()
  if (key === 'agreements') loadAgreements()
  if (key === 'receipts') loadReceipts()
  if (key === 'acceptances') loadAcceptances()
}

onMounted(async () => {
  const [sup, wh, mat]: any[] = await Promise.all([
    getAllSuppliers(), getWarehouseTree(), pageMaterials({ page: 1, size: 500 }),
  ])
  supplierOptions.value = sup.map((s: any) => ({ value: s.id, label: `${s.code} ${s.name}` }))
  warehouseOptions.value = wh.map((w: any) => ({ value: w.id, label: w.name }))
  materialOptions.value = (mat.records || mat).map((m: any) => ({ value: m.id, label: `${m.code} ${m.name}` }))
  loadRequests()
})
</script>

<style scoped>
.page-card { background: #fff; border-radius: 8px; padding: 16px; }
.seg-tabs { display: flex; gap: 4px; margin-bottom: 14px; border-bottom: 1px solid rgba(17, 24, 39, 0.08); }
.seg-tab { padding: 8px 16px; font-size: 13px; color: #4b5563; cursor: pointer; border-bottom: 2px solid transparent; }
.seg-tab.active { color: #2563eb; border-bottom-color: #2563eb; font-weight: 500; }
.toolbar { display: flex; gap: 8px; margin-bottom: 12px; }
.w180 { width: 180px; }
.form-row { display: flex; align-items: center; gap: 8px; margin-bottom: 12px; }
.form-row label { font-size: 13px; color: #4b5563; }
.line-header { display: flex; justify-content: space-between; align-items: center; margin: 8px 0; font-size: 13px; color: #4b5563; }
.line-grid-head { font-size: 12px; color: #6b7280; }
.line-grid5 { display: grid; grid-template-columns: 2fr 1fr 1fr 1fr 40px; gap: 8px; margin-bottom: 8px; align-items: center; }
.line-grid3 { display: grid; grid-template-columns: 2fr 1fr 40px; gap: 8px; margin-bottom: 8px; align-items: center; }
.line-grid3b { display: grid; grid-template-columns: 2fr 1fr 1fr; gap: 8px; margin-bottom: 8px; align-items: center; }
.line-grid4b { display: grid; grid-template-columns: 2fr 1fr 0.7fr 40px; gap: 8px; margin-bottom: 8px; align-items: center; }
.line-grid4 { display: grid; grid-template-columns: 2fr 1.5fr 1.2fr 0.8fr; gap: 8px; margin-bottom: 8px; align-items: center; }
.line-grid5b { display: grid; grid-template-columns: 2fr 0.5fr 0.7fr 0.7fr 1.5fr; gap: 8px; margin-bottom: 8px; align-items: center; }
.amount { font-size: 13px; color: #374151; }
.total-row { text-align: right; font-size: 13px; font-weight: 600; color: #111827; margin-top: 8px; }
.detail p { margin: 0 0 12px; font-size: 13px; color: #374151; }
.receive-box { margin-top: 14px; border-top: 1px dashed rgba(17, 24, 39, 0.08); padding-top: 10px; }
.detail-actions { display: flex; gap: 8px; margin-top: 16px; justify-content: flex-end; }
.ex-form { display: flex; gap: 8px; margin-bottom: 12px; align-items: center; }
</style>
