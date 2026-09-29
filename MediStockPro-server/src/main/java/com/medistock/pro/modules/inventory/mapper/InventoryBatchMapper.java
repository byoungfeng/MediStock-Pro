package com.medistock.pro.modules.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.medistock.pro.modules.inventory.entity.InventoryBatch;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface InventoryBatchMapper extends BaseMapper<InventoryBatch> {

    /** 行锁读取 (库存引擎专用: 同事务内串行化并发) */
    @Select("SELECT * FROM inventory_batch WHERE id = #{id} FOR UPDATE")
    InventoryBatch selectByIdForUpdate(@Param("id") Long id);

    /** 库存总览聚合 (低库存: 可用<安全库存; 近效期: 效期<=今天+days); warehouseIds 为数据权限范围(null 不限) */
    @Select("""
            <script>
            SELECT COALESCE(SUM(b.on_hand),0) AS totalOnHand,
                   COALESCE(SUM(b.on_hand - b.locked_qty),0) AS totalAvailable,
                   COALESCE(SUM(b.locked_qty),0) AS totalLocked,
                   COALESCE(SUM(b.in_transit_qty),0) AS totalInTransit,
                   COALESCE(SUM(b.on_hand * b.unit_cost),0) AS totalValue,
                   COUNT(DISTINCT b.material_id) AS materialKinds,
                   COALESCE(SUM(CASE WHEN m.safety_qty IS NOT NULL AND (b.on_hand - b.locked_qty) &lt; m.safety_qty THEN 1 ELSE 0 END),0) AS lowStockCount,
                   COALESCE(SUM(CASE WHEN b.expiry_date IS NOT NULL AND b.on_hand > 0
                                     AND b.expiry_date &lt;= DATE_ADD(CURDATE(), INTERVAL #{days} DAY) THEN 1 ELSE 0 END),0) AS nearExpiryCount
            FROM inventory_batch b
            LEFT JOIN material m ON m.id = b.material_id AND m.deleted = 0
            WHERE (#{warehouseId} IS NULL OR b.warehouse_id = #{warehouseId})
            <if test="warehouseIds != null">
              AND b.warehouse_id IN
              <choose>
                <when test="warehouseIds.size() > 0">
                  <foreach collection="warehouseIds" item="wid" open="(" separator="," close=")">#{wid}</foreach>
                </when>
                <otherwise>(-1)</otherwise>
              </choose>
            </if>
            </script>
            """)
    Map<String, Object> summary(@Param("warehouseId") Long warehouseId, @Param("days") int days,
                                @Param("warehouseIds") List<Long> warehouseIds);

    /** 库存分析-分类分布 (按金额降序) */
    @Select("""
            SELECT COALESCE(c.name, '未分类') AS categoryName,
                   COALESCE(SUM(b.on_hand),0) AS qty,
                   COALESCE(SUM(b.on_hand * b.unit_cost),0) AS value
            FROM inventory_batch b
            JOIN material m ON m.id = b.material_id AND m.deleted = 0
            LEFT JOIN material_category c ON c.id = m.category_id
            WHERE b.on_hand > 0
              AND (#{warehouseId} IS NULL OR b.warehouse_id = #{warehouseId})
            GROUP BY c.name
            ORDER BY value DESC
            """)
    List<Map<String, Object>> inventoryByCategory(@Param("warehouseId") Long warehouseId);

    /** 库存分析-物资金额 Top10 */
    @Select("""
            SELECT m.code AS materialCode, m.name AS materialName, m.spec AS spec,
                   COALESCE(SUM(b.on_hand),0) AS qty,
                   COALESCE(SUM(b.on_hand * b.unit_cost),0) AS value
            FROM inventory_batch b
            JOIN material m ON m.id = b.material_id AND m.deleted = 0
            WHERE b.on_hand > 0
              AND (#{warehouseId} IS NULL OR b.warehouse_id = #{warehouseId})
            GROUP BY m.id, m.code, m.name, m.spec
            ORDER BY value DESC
            LIMIT 10
            """)
    List<Map<String, Object>> inventoryTopMaterials(@Param("warehouseId") Long warehouseId);

    /** 库存预警 (P047): 可用量低于安全库存的 物资×仓库 (含断货) */
    @Select("""
            SELECT m.id AS materialId, m.code AS materialCode, m.name AS materialName, m.spec,
                   b.warehouse_id AS warehouseId, w.name AS warehouseName,
                   COALESCE(SUM(b.on_hand - b.locked_qty),0) AS availableQty,
                   m.safety_qty AS safetyQty,
                   m.safety_qty - COALESCE(SUM(b.on_hand - b.locked_qty),0) AS gapQty,
                   CASE WHEN COALESCE(SUM(b.on_hand - b.locked_qty),0) <= 0 THEN 'STOCKOUT' ELSE 'LOW' END AS alertType
            FROM inventory_batch b
            JOIN material m ON m.id = b.material_id AND m.deleted = 0
            JOIN warehouse w ON w.id = b.warehouse_id
            WHERE m.safety_qty IS NOT NULL AND m.safety_qty > 0
              AND (#{warehouseId} IS NULL OR b.warehouse_id = #{warehouseId})
            GROUP BY m.id, m.code, m.name, m.spec, b.warehouse_id, w.name, m.safety_qty
            HAVING COALESCE(SUM(b.on_hand - b.locked_qty),0) < m.safety_qty
            ORDER BY gapQty DESC
            """)
    List<Map<String, Object>> stockLevelAlerts(@Param("warehouseId") Long warehouseId);

    /** 近效期分析-分档分布 (数量/金额) */
    @Select("""
            SELECT CASE WHEN b.expiry_date < CURDATE() THEN 'EXPIRED'
                        WHEN b.expiry_date <= DATE_ADD(CURDATE(), INTERVAL 30 DAY) THEN 'D30'
                        WHEN b.expiry_date <= DATE_ADD(CURDATE(), INTERVAL 60 DAY) THEN 'D60'
                        WHEN b.expiry_date <= DATE_ADD(CURDATE(), INTERVAL 90 DAY) THEN 'D90'
                        ELSE 'OVER90' END AS bucket,
                   COUNT(*) AS batchCount,
                   COALESCE(SUM(b.on_hand),0) AS qty,
                   COALESCE(SUM(b.on_hand * b.unit_cost),0) AS value
            FROM inventory_batch b
            WHERE b.on_hand > 0 AND b.expiry_date IS NOT NULL
              AND (#{warehouseId} IS NULL OR b.warehouse_id = #{warehouseId})
            GROUP BY bucket
            """)
    List<Map<String, Object>> expiryDistribution(@Param("warehouseId") Long warehouseId);
}
