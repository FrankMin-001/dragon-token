package com.smalldragon.yml.exceptions;

/**
 * 登录认证异常
 * 用于处理用户未登录或登录失效的情况
 */
public class AuthenticationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private Integer code;
    private String message;

    public AuthenticationException() {
        super("未登录或登录已失效");
        this.code = 401;
        this.message = "未登录或登录已失效";
    }

    public AuthenticationException(String message) {
        super(message);
        this.code = 401;
        this.message = message;
    }

    public AuthenticationException(Integer code, String message) {
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
