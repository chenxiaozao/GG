package com.xiaozao.robotmall.common;

/** 统一封装接口状态、提示信息和响应数据。 */
public class ApiResponse<T> {
    private int code;
    private String message;
    private T data;

    /** 构造指定状态、提示和数据的响应。 */
    public ApiResponse(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    /** 前后端统一约定：code=200 表示成功，其他值表示失败。 */
    /** 构造包含数据的成功响应。 */
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(200, "success", data);
    }

    /** 构造无数据的成功响应。 */
    public static <T> ApiResponse<T> ok() {
        return new ApiResponse<>(200, "success", null);
    }

    /** 构造带提示的业务失败响应。 */
    public static <T> ApiResponse<T> fail(String message) {
        return new ApiResponse<>(400, message, null);
    }

    /** 读取响应状态码。 */
    public int getCode() {
        return code;
    }

    /** 设置响应状态码。 */
    public void setCode(int code) {
        this.code = code;
    }

    /** 读取提示信息。 */
    public String getMessage() {
        return message;
    }

    /** 设置提示信息。 */
    public void setMessage(String message) {
        this.message = message;
    }

    /** 读取响应数据。 */
    public T getData() {
        return data;
    }

    /** 设置响应数据。 */
    public void setData(T data) {
        this.data = data;
    }
}
