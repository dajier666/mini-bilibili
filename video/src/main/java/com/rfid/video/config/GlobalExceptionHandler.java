package com.rfid.video.config;

import com.rfid.video.entity.Result;
import com.rfid.video.exception.VideoException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;


/**
 * 全局异常处理器
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    /**
     * 处理视频相关异常
     */
    @ExceptionHandler(VideoException.class)
    public Result handleBusinessException(VideoException e) {
        return Result.error(e.getMessage());
    }
    


}