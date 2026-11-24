package com.xiaozao.robotmall.common;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 统一转换控制器抛出的异常。 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    /** 保留显式的 HTTP 状态及提示。 */
    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public ResponseEntity<ApiResponse<?>> handleStatus(
            org.springframework.web.server.ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode())
                .body(new ApiResponse<>(e.getStatusCode().value(), e.getReason(), null));
    }

    /** 将格式不正确的请求转成参数错误。 */
    @ExceptionHandler({
        org.springframework.http.converter.HttpMessageNotReadableException.class,
        org.springframework.web.bind.ServletRequestBindingException.class,
        org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<ApiResponse<?>> handleInvalidInput(Exception e) {
        return ResponseEntity.badRequest().body(ApiResponse.fail("请求参数格式不正确，请检查输入"));
    }

    /** 将静态资源不存在转换为未找到响应。 */
    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<?>> handleMissingResource(Exception e) {
        return ResponseEntity.status(404).body(ApiResponse.fail("资源不存在"));
    }

    /** 将业务异常转成可展示的提示。 */
    @ExceptionHandler(BizException.class)
    public ResponseEntity<ApiResponse<?>> handleBizException(BizException e) {
        return ResponseEntity.badRequest().body(ApiResponse.fail(e.getMessage()));
    }

    /** 返回通用服务器错误并隐藏内部异常细节。 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<?>> handleException(Exception e) {
        return ResponseEntity.status(500).body(ApiResponse.fail("服务器内部错误"));
    }
}
