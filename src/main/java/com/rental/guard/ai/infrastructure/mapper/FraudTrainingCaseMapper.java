/**
 * @author qkcao
 * @date 2025/9/16 18:17
 */
package com.rental.guard.ai.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.rental.guard.ai.infrastructure.po.PoFraudTrainingCase;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface FraudTrainingCaseMapper extends BaseMapper<PoFraudTrainingCase> {

    List<PoFraudTrainingCase> getALlPoFraudTrainingCase();
}
