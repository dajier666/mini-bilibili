package com.rfid.user.config;

import com.rfid.user.entity.Result;
import com.rfid.user.exception.BusinessException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.sql.SQLException;


/**
 * 全局异常处理器
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    @ExceptionHandler(BusinessException.class)
    public Result handleBusinessException(BusinessException e) {
        return Result.error(e.getMessage());
    }
    
    /**
     * 处理 SQL 相关异常
     */
    @ExceptionHandler({SQLException.class, DataAccessException.class})
    public Result handleSQLException(Exception e) {
        if (e instanceof DuplicateKeyException) {
            return Result.error("数据库中已存在相同记录，请检查后重试");
        }
        return Result.error("数据库操作失败: " + e.getMessage());
    }
}