package com.feijimiao.xianyuassistant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.feijimiao.xianyuassistant.entity.XianyuBuyerInteractionRecord;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface XianyuBuyerInteractionRecordMapper extends BaseMapper<XianyuBuyerInteractionRecord> {
    @Insert("INSERT OR IGNORE INTO xianyu_buyer_interaction_record (xianyu_account_id, order_id, action, sid, buyer_user_id, xy_goods_id, content, state, attempts, next_run_at, updated_at) VALUES (#{xianyuAccountId}, #{orderId}, #{action}, #{sid}, #{buyerUserId}, #{xyGoodsId}, #{content}, 0, 0, #{nextRunAt}, #{updatedAt})")
    int enqueue(XianyuBuyerInteractionRecord record);
    @Select("SELECT * FROM xianyu_buyer_interaction_record WHERE state = 0 AND next_run_at <= #{now} ORDER BY next_run_at LIMIT 30")
    List<XianyuBuyerInteractionRecord> due(@Param("now") long now);
    @Update("UPDATE xianyu_buyer_interaction_record SET state = 2, updated_at = #{now} WHERE id = #{id} AND state = 0")
    int claim(@Param("id") Long id, @Param("now") long now);
    @Update("UPDATE xianyu_buyer_interaction_record SET state = -1, result_message = '执行中断，结果未知，请核实平台状态后再重试' WHERE state = 2 AND updated_at < #{before}")
    int recoverInterrupted(@Param("before") long before);
    @Update("UPDATE xianyu_buyer_interaction_record SET state = 0, attempts = 0, next_run_at = #{now}, updated_at = #{now} WHERE id = #{id} AND xianyu_account_id = #{accountId} AND state = -1")
    int retry(@Param("id") Long id, @Param("accountId") Long accountId, @Param("now") long now);
}
