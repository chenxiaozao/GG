package com.xiaozao.robotmall.common;

/** 表示由客户端可理解的业务规则错误。 */
public class BizException extends RuntimeException {

    /** 初始化当前对象及其依赖。 */
    public BizException(String message) {
        super(message);
    }
}
