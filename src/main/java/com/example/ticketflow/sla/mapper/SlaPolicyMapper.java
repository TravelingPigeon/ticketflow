package com.example.ticketflow.sla.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.ticketflow.sla.domain.SlaPolicy;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SlaPolicyMapper extends BaseMapper<SlaPolicy> {
}