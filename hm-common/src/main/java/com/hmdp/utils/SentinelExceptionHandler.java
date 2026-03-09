package com.hmdp.utils;

import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeException;
import com.alibaba.csp.sentinel.slots.block.flow.FlowException;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowException;
import com.alibaba.csp.sentinel.slots.system.SystemBlockException;
import com.hmdp.dto.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局 Sentinel 限流降级异常处理器
 */
@Slf4j
@RestControllerAdvice
public class SentinelExceptionHandler {

    /**
     * 处理 Sentinel 限流异常（QPS超限）
     */
    @ExceptionHandler(FlowException.class)
    public Result handleFlowException(FlowException ex) {
        log.warn("触发限流: {}", ex.getMessage());
        return Result.fail("当前访问过于频繁，请稍后再试");
    }

    /**
     * 处理 Sentinel 降级异常（异常比例/响应时间触发熔断）
     */
    @ExceptionHandler(DegradeException.class)
    public Result handleDegradeException(DegradeException ex) {
        log.warn("触发降级: {}", ex.getMessage());
        return Result.fail("服务暂时不可用，请稍后再试");
    }

    /**
     * 处理热点参数限流异常
     */
    @ExceptionHandler(ParamFlowException.class)
    public Result handleParamFlowException(ParamFlowException ex) {
        log.warn("触发热点参数限流: {}", ex.getMessage());
        return Result.fail("当前操作过于频繁，请稍后再试");
    }

    /**
     * 处理系统规则异常（系统负载保护）
     */
    @ExceptionHandler(SystemBlockException.class)
    public Result handleSystemBlockException(SystemBlockException ex) {
        log.warn("触发系统保护: {}", ex.getMessage());
        return Result.fail("系统繁忙，请稍后再试");
    }

    /**
     * 处理通用 Sentinel 阻塞异常
     */
    @ExceptionHandler(BlockException.class)
    public Result handleBlockException(BlockException ex) {
        log.warn("触发Sentinel阻断: {}", ex.getMessage());
        return Result.fail("请求被拦截，请稍后再试");
    }
}
