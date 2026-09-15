package com.example.ticketflow.role.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/** 权限字典。全局配置，不含 tenant_id，编码格式为「资源:动作」。 */
@TableName("tf_permission")
public class Permission {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String code;

    private String name;

    private String permissionGroup;

    private String description;

    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPermissionGroup() { return permissionGroup; }
    public void setPermissionGroup(String permissionGroup) { this.permissionGroup = permissionGroup; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}