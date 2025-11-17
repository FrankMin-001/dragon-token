package com.smalldragon.yml.exceptions;

/**
 * 权限不足异常
 * 用于处理用户没有访问权限的情况
 */
public class PermissionDeniedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private Integer code;
    private String message;

    public PermissionDeniedException() {
        super("权限不足");
        this.code = 403;
        this.message = "权限不足";
    }

    public PermissionDeniedException(String message) {
        super(message);
        this.code = 403;
        this.message = message;
    }

    public PermissionDeniedException(Integer code, String message) {
        super(message);
        this.code = code;
        this.message = message;
    }

    // Getter 方法
    public Integer getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
