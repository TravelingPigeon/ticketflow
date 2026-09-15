package com.example.ticketflow.role.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.ticketflow.role.domain.Permission;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PermissionMapper extends BaseMapper<Permission> {
}
