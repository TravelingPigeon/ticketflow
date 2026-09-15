package com.example.ticketflow.role.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.ticketflow.role.domain.Role;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RoleMapper extends BaseMapper<Role> {
}