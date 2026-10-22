package com.servpp.service;

import java.util.Map;

/**
 * @author lidong@date 2023-11-03@version 1.0
 */
public interface RoleMapper {
    void roleToEntity(Role role, Map<String, Entity> entities);

    void entityToRole(Map<String, Entity> entities,Role role);
}
