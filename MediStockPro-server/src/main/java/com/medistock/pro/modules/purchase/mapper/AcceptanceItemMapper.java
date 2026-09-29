package com.medistock.pro.modules.purchase.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.medistock.pro.modules.purchase.entity.AcceptanceItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface AcceptanceItemMapper extends BaseMapper<AcceptanceItem> {

    /** 供应商绩效-验收合格率 (按已完成验收单) */
    @Select("""
            SELECT o.supplier_id AS supplierId,
                   COALESCE(SUM(ai.received_qty),0) AS receivedQty,
                   COALESCE(SUM(ai.accepted_qty),0) AS acceptedQty
            FROM acceptance_item ai
            JOIN acceptance a ON a.id = ai.acceptance_id
            JOIN receipt r ON r.id = a.receipt_id
            JOIN purchase_order o ON o.id = r.order_id
            WHERE a.status = 'COMPLETED'
            GROUP BY o.supplier_id
            """)
    List<Map<String, Object>> supplierQuality();
}
