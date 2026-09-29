package com.medistock.pro.modules.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.medistock.pro.modules.inventory.entity.InventoryTxn;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Mapper
public interface InventoryTxnMapper extends BaseMapper<InventoryTxn> {

    /**
     * 收发存汇总: 只统计影响 on_hand 的流水类型 (排除 LOCK/UNLOCK 锁定流水)
     * 期初 = from 之前 IN-OUT 净额; 期末 = 期初 + 期间入 - 期间出
     */
    @Select("""
            SELECT material_id AS materialId,
                   SUM(CASE WHEN created_at < #{from} THEN CASE WHEN direction = 'IN' THEN qty ELSE -qty END ELSE 0 END) AS openingQty,
                   SUM(CASE WHEN created_at >= #{from} AND created_at < DATE_ADD(#{to}, INTERVAL 1 DAY) AND direction = 'IN' THEN qty ELSE 0 END) AS inQty,
                   SUM(CASE WHEN created_at >= #{from} AND created_at < DATE_ADD(#{to}, INTERVAL 1 DAY) AND direction = 'OUT' THEN qty ELSE 0 END) AS outQty
            FROM inventory_txn
            -- 不含 TRANSFER_LOSS: 损耗只核减在途, 不影响 on_hand (发运时已出库)
            WHERE source_type IN ('INBOUND','ISSUE','REVERSAL','RETURN','SCRAP','TRANSFER_SHIP','TRANSFER_RECEIVE','COUNT','ADJUST')
              AND (#{warehouseId} IS NULL OR warehouse_id = #{warehouseId})
            GROUP BY material_id
            ORDER BY material_id
            """)
    List<Map<String, Object>> inoutSummary(@Param("warehouseId") Long warehouseId,
                                           @Param("from") LocalDate from,
                                           @Param("to") LocalDate to);

    /** 仪表盘趋势: 近 N 日每日入/出量 (仅影响 on_hand 的流水); warehouseIds 为数据权限范围(null 不限) */
    @Select("""
            <script>
            SELECT DATE(created_at) AS day,
                   SUM(CASE WHEN direction = 'IN' THEN qty ELSE 0 END) AS inQty,
                   SUM(CASE WHEN direction = 'OUT' THEN qty ELSE 0 END) AS outQty
            FROM inventory_txn
            WHERE source_type IN ('INBOUND','ISSUE','REVERSAL','RETURN','SCRAP','TRANSFER_SHIP','TRANSFER_RECEIVE','COUNT','ADJUST')
              AND created_at >= DATE_SUB(CURDATE(), INTERVAL #{days} - 1 DAY)
              AND (#{warehouseId} IS NULL OR warehouse_id = #{warehouseId})
            <if test="warehouseIds != null">
              AND warehouse_id IN
              <choose>
                <when test="warehouseIds.size() > 0">
                  <foreach collection="warehouseIds" item="wid" open="(" separator="," close=")">#{wid}</foreach>
                </when>
                <otherwise>(-1)</otherwise>
              </choose>
            </if>
            GROUP BY DATE(created_at)
            ORDER BY day
            </script>
            """)
    List<Map<String, Object>> dailyTrend(@Param("warehouseId") Long warehouseId, @Param("days") int days,
                                         @Param("warehouseIds") List<Long> warehouseIds);

    /** 领用分析-按日趋势 */
    @Select("""
            SELECT DATE(t.created_at) AS day, COALESCE(SUM(t.qty),0) AS qty
            FROM inventory_txn t
            WHERE t.source_type = 'ISSUE' AND t.direction = 'OUT'
              AND t.created_at >= #{from} AND t.created_at < DATE_ADD(#{to}, INTERVAL 1 DAY)
              AND (#{warehouseId} IS NULL OR t.warehouse_id = #{warehouseId})
            GROUP BY DATE(t.created_at)
            ORDER BY day
            """)
    List<Map<String, Object>> issueTrend(@Param("warehouseId") Long warehouseId,
                                         @Param("from") LocalDate from, @Param("to") LocalDate to);

    /** 领用分析-科室排行 */
    @Select("""
            SELECT io.department_id AS departmentId, COALESCE(ou.name, '未指定科室') AS departmentName,
                   COALESCE(SUM(t.qty),0) AS qty
            FROM inventory_txn t
            JOIN issue_order io ON io.id = t.source_id
            LEFT JOIN org_unit ou ON ou.id = io.department_id
            WHERE t.source_type = 'ISSUE' AND t.direction = 'OUT'
              AND t.created_at >= #{from} AND t.created_at < DATE_ADD(#{to}, INTERVAL 1 DAY)
              AND (#{warehouseId} IS NULL OR t.warehouse_id = #{warehouseId})
            GROUP BY io.department_id, ou.name
            ORDER BY qty DESC
            LIMIT 10
            """)
    List<Map<String, Object>> issueByDepartment(@Param("warehouseId") Long warehouseId,
                                                @Param("from") LocalDate from, @Param("to") LocalDate to);

    /** 领用分析-物资 Top10 */
    @Select("""
            SELECT m.code AS materialCode, m.name AS materialName, COALESCE(SUM(t.qty),0) AS qty
            FROM inventory_txn t
            JOIN material m ON m.id = t.material_id
            WHERE t.source_type = 'ISSUE' AND t.direction = 'OUT'
              AND t.created_at >= #{from} AND t.created_at < DATE_ADD(#{to}, INTERVAL 1 DAY)
              AND (#{warehouseId} IS NULL OR t.warehouse_id = #{warehouseId})
            GROUP BY m.id, m.code, m.name
            ORDER BY qty DESC
            LIMIT 10
            """)
    List<Map<String, Object>> issueTopMaterials(@Param("warehouseId") Long warehouseId,
                                                @Param("from") LocalDate from, @Param("to") LocalDate to);

    /** 库存变动趋势: 近 N 日按业务类型净变动 */
    @Select("""
            SELECT DATE(created_at) AS day, source_type AS sourceType,
                   SUM(CASE WHEN direction = 'IN' THEN qty ELSE -qty END) AS netQty
            FROM inventory_txn
            WHERE source_type IN ('INBOUND','ISSUE','REVERSAL','RETURN','SCRAP','TRANSFER_SHIP','TRANSFER_RECEIVE','COUNT','ADJUST')
              AND created_at >= DATE_SUB(CURDATE(), INTERVAL #{days} - 1 DAY)
              AND (#{warehouseId} IS NULL OR warehouse_id = #{warehouseId})
            GROUP BY DATE(created_at), source_type
            ORDER BY day
            """)
    List<Map<String, Object>> movementTrend(@Param("warehouseId") Long warehouseId, @Param("days") int days);
}
