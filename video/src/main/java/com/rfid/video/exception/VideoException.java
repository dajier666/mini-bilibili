package com.rfid.video.exception;

/**
 * 自定义业务异常类
 */
public class VideoException extends RuntimeException {
    public VideoException() {
        super();
    }

    public VideoException(String message) {
        super(message);
    }

    public VideoException(String message, Throwable cause) {
        super(message, cause);
    }

    public VideoException(Throwable cause) {
        super(cause);
    }

    protected VideoException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}