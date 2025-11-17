package com.smalldragon.yml.core;

import cn.hutool.core.util.ObjectUtil;
import com.smalldragon.yml.annotations.DragonCheckMode;
import com.smalldragon.yml.context.DragonContextHolder;
import com.smalldragon.yml.context.UserContext;
import com.smalldragon.yml.exceptions.PermissionDeniedException;
import com.smalldragon.yml.utils.ExceptionHandlerUtils;
import com.smalldragon.yml.utils.ValidationUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.List;

/**
 * Dragon权限工具类 - 提供编程式权限校验方法
 * 替代注解方式，支持在代码中直接进行权限和角色校验
 */
@Component
public class DragonUtils {
    private static final Logger logger = LoggerFactory.getLogger(DragonUtils.class);

    @Resource
    private StpInterface stpInterface;

    /**
     * 检查当前用户是否拥有指定权限（AND模式）
     * 
     * @param permissions 需要检查的权限列表
     * @return true-拥有所有权限，false-缺少权限
     */
    public boolean checkPermission(String... permissions) {
        return checkPermissionWithMode(permissions, DragonCheckMode.AND);
    }

    /**
     * 检查当前用户是否拥有指定权限（指定模式）
     * 
     * @param permissions 需要检查的权限列表
     * @param mode 校验模式（AND/OR）
     * @return true-校验通过，false-校验失败
     */
    public boolean checkPermissionWithMode(String[] permissions, DragonCheckMode mode) {
        if (ValidationUtils.isArrayEmpty(permissions)) {
            return true;
        }

        UserContext userContext = DragonContextHolder.getContext();
        if (ValidationUtils.isNull(userContext) || ValidationUtils.isObjectEmpty(userContext.getUserId())) {
            logger.warn("用户上下文为空，权限校验失败");
            return false;
        }

        List<String> userPermissions = stpInterface.getPermissionList(userContext.getUserId());
        return checkPermissions(userPermissions, permissions, mode);
    }

    /**
     * 检查当前用户是否拥有指定角色（AND模式）
     * 
     * @param roles 需要检查的角色列表
     * @return true-拥有所有角色，false-缺少角色
     */
    public boolean checkRole(String... roles) {
        return checkRoleWithMode(roles, DragonCheckMode.AND);
    }

    /**
     * 检查当前用户是否拥有指定角色（指定模式）
     * 
     * @param roles 需要检查的角色列表
     * @param mode 校验模式（AND/OR）
     * @return true-校验通过，false-校验失败
     */
    public boolean checkRoleWithMode(String[] roles, DragonCheckMode mode) {
        if (ValidationUtils.isArrayEmpty(roles)) {
            return true;
        }

        UserContext userContext = DragonContextHolder.getContext();
        if (ValidationUtils.isNull(userContext) || ValidationUtils.isObjectEmpty(userContext.getUserId())) {
            logger.warn("用户上下文为空，角色校验失败");
            return false;
        }

        List<String> userRoles = stpInterface.getRoleList(userContext.getUserId());
        return checkPermissions(userRoles, roles, mode);
    }

    /**
     * 检查权限并抛出异常（AND模式）
     * 如果校验失败，直接抛出PermissionDeniedException
     * 
     * @param permissions 需要检查的权限列表
     * @throws PermissionDeniedException 权限不足时抛出
     */
    public void checkPermissionOrThrow(String... permissions) throws PermissionDeniedException {
        if (!checkPermission(permissions)) {
            throw new PermissionDeniedException("权限不足，需要权限：" + Arrays.toString(permissions));
        }
    }

    /**
     * 检查角色并抛出异常（AND模式）
     * 如果校验失败，直接抛出PermissionDeniedException
     * 
     * @param roles 需要检查的角色列表
     * @throws PermissionDeniedException 角色不足时抛出
     */
    public void checkRoleOrThrow(String... roles) throws PermissionDeniedException {
        if (!checkRole(roles)) {
            throw new PermissionDeniedException("角色不足，需要角色：" + Arrays.toString(roles));
        }
    }

    /**
     * 检查权限并抛出异常（指定模式）
     * 
     * @param permissions 需要检查的权限列表
     * @param mode 校验模式
     * @throws PermissionDeniedException 权限不足时抛出
     */
    public void checkPermissionOrThrowWithMode(String[] permissions, DragonCheckMode mode) throws PermissionDeniedException {
        if (!checkPermissionWithMode(permissions, mode)) {
            String modeStr = mode == DragonCheckMode.AND ? "所有" : "任意一个";
            throw new PermissionDeniedException("权限不足，需要" + modeStr + "权限：" + Arrays.toString(permissions));
        }
    }

    /**
     * 检查角色并抛出异常（指定模式）
     * 
     * @param roles 需要检查的角色列表
     * @param mode 校验模式
     * @throws PermissionDeniedException 角色不足时抛出
     */
    public void checkRoleOrThrowWithMode(String[] roles, DragonCheckMode mode) throws PermissionDeniedException {
        if (!checkRoleWithMode(roles, mode)) {
            String modeStr = mode == DragonCheckMode.AND ? "所有" : "任意一个";
            throw new PermissionDeniedException("角色不足，需要" + modeStr + "角色：" + Arrays.toString(roles));
        }
    }

    /**
     * 获取当前用户的权限列表
     * 
     * @return 当前用户的权限列表
     */
    public List<String> getCurrentUserPermissions() {
        UserContext userContext = DragonContextHolder.getContext();
        if (userContext == null || ObjectUtil.isEmpty(userContext.getUserId())) {
            logger.warn("用户上下文为空，无法获取权限列表");
            return null;
        }
        return stpInterface.getPermissionList(userContext.getUserId());
    }

    /**
     * 获取当前用户的角色列表
     * 
     * @return 当前用户的角色列表
     */
    public List<String> getCurrentUserRoles() {
        UserContext userContext = DragonContextHolder.getContext();
        if (userContext == null || ObjectUtil.isEmpty(userContext.getUserId())) {
            logger.warn("用户上下文为空，无法获取角色列表");
            return null;
        }
        return stpInterface.getRoleList(userContext.getUserId());
    }

    /**
     * 获取当前用户ID
     * 
     * @return 当前用户ID
     */
    public String getCurrentUserId() {
        UserContext userContext = DragonContextHolder.getContext();
        return userContext != null ? userContext.getUserId() : null;
    }

    /**
     * 检查用户是否拥有指定权限
     * 
     * @param userPermissions 用户拥有的权限列表
     * @param requiredPermissions 需要检查的权限列表
     * @param mode 校验模式
     * @return true-校验通过，false-校验失败
     */
    private boolean checkPermissions(List<String> userPermissions, String[] requiredPermissions, DragonCheckMode mode) {
        if (userPermissions == null || userPermissions.isEmpty()) {
            return false;
        }

        if (mode == DragonCheckMode.AND) {
            // AND 模式：需要拥有所有要求的权限
            return Arrays.stream(requiredPermissions)
                    .allMatch(requiredPermission -> hasPermission(userPermissions, requiredPermission));
        } else {
            // OR 模式：只需要拥有任意一个要求的权限
            return Arrays.stream(requiredPermissions)
                    .anyMatch(requiredPermission -> hasPermission(userPermissions, requiredPermission));
        }
    }

    /**
     * 检查用户是否拥有特定权限
     */
    private boolean hasPermission(List<String> userPermissions, String requiredPermission) {
        return userPermissions.stream()
                .anyMatch(userPermission -> userPermission.equals(requiredPermission));
    }
}