package com.medistock.pro.modules.purchase.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.medistock.pro.modules.purchase.entity.PurchaseOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Mapper
public interface PurchaseOrderMapper extends BaseMapper<PurchaseOrder> {

    /** 采购分析-趋势: 已生效订单(审批通过及以后)按日聚合金额/数量 */
    @Select("""
            SELECT DATE(o.created_at) AS day,
                   COALESCE(SUM(i.ordered_qty * i.unit_price),0) AS amount,
                   COALESCE(SUM(i.ordered_qty),0) AS qty
            FROM purchase_order o
            JOIN purchase_order_item i ON i.order_id = o.id
            WHERE o.status IN ('APPROVED','RECEIVING','COMPLETED')
              AND o.deleted = 0
              AND o.created_at >= #{from} AND o.created_at < DATE_ADD(#{to}, INTERVAL 1 DAY)
            GROUP BY DATE(o.created_at)
            ORDER BY day
            """)
    List<Map<String, Object>> purchaseTrend(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /** 采购分析-供应商 Top10 (按金额) */
    @Select("""
            SELECT o.supplier_id AS supplierId, s.name AS supplierName,
                   COALESCE(SUM(i.ordered_qty * i.unit_price),0) AS amount,
                   COALESCE(SUM(i.ordered_qty),0) AS qty
            FROM purchase_order o
            JOIN purchase_order_item i ON i.order_id = o.id
            JOIN supplier s ON s.id = o.supplier_id
            WHERE o.status IN ('APPROVED','RECEIVING','COMPLETED')
              AND o.deleted = 0
              AND o.created_at >= #{from} AND o.created_at < DATE_ADD(#{to}, INTERVAL 1 DAY)
            GROUP BY o.supplier_id, s.name
            ORDER BY amount DESC
            LIMIT 10
            """)
    List<Map<String, Object>> purchaseTopSuppliers(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /** 到货预警 (P048): 已生效未完结且已过承诺交期的订单 */
    @Select("""
            SELECT o.id, o.order_no AS orderNo, o.status, o.expect_date AS expectDate,
                   DATEDIFF(CURDATE(), o.expect_date) AS overdueDays,
                   o.total_qty AS totalQty, s.name AS supplierName
            FROM purchase_order o
            JOIN supplier s ON s.id = o.supplier_id
            WHERE o.status IN ('APPROVED','RECEIVING')
              AND o.expect_date IS NOT NULL AND o.expect_date < CURDATE()
              AND o.deleted = 0
            ORDER BY overdueDays DESC
            """)
    List<Map<String, Object>> arrivalAlerts();

    /** 供应商绩效-订单规模 */
    @Select("""
            SELECT o.supplier_id AS supplierId, s.name AS supplierName,
                   COUNT(DISTINCT o.id) AS orderCount,
                   COALESCE(SUM(i.ordered_qty * i.unit_price),0) AS totalAmount
            FROM purchase_order o
            JOIN purchase_order_item i ON i.order_id = o.id
            JOIN supplier s ON s.id = o.supplier_id
            WHERE o.status IN ('APPROVED','RECEIVING','COMPLETED') AND o.deleted = 0
            GROUP BY o.supplier_id, s.name
            """)
    List<Map<String, Object>> supplierOrderStats();

    /** 供应商绩效-到货准时率 (按收货单) */
    @Select("""
            SELECT o.supplier_id AS supplierId,
                   COUNT(*) AS receiptCount,
                   SUM(CASE WHEN r.arrival_date <= o.expect_date THEN 1 ELSE 0 END) AS onTimeCount
            FROM receipt r
            JOIN purchase_order o ON o.id = r.order_id
            WHERE o.expect_date IS NOT NULL
            GROUP BY o.supplier_id
            """)
    List<Map<String, Object>> supplierPunctuality();
}
