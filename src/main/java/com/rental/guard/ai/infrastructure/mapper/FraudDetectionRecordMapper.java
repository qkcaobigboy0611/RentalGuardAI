/**
 * @author qkcao
 * @date 2025/9/16 18:13
 */
package com.rental.guard.ai.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.rental.guard.ai.infrastructure.po.PoFraudDetectionRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface FraudDetectionRecordMapper extends BaseMapper<PoFraudDetectionRecord> {
    @Select("SELECT * " +
            "FROM fraud_detection_record " +
            "WHERE create_time >= DATE_SUB(NOW(), INTERVAL #{days} DAY)")
    List<PoFraudDetectionRecord> getPoFraudDetectionRecords(Integer days);
}
