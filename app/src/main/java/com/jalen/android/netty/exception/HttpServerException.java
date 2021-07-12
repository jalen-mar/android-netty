package com.jalen.android.netty.exception;

import com.alibaba.fastjson.JSON;

public class HttpServerException extends HttpException {
    private String message;

    public HttpServerException(String message) {
        super(500, message);
    }

    public HttpServerException(int code, Object message) {
        super(code, null);
        if (message instanceof String) {
            this.message = (String) message;
        } else {
            this.message = JSON.toJSONString(message);
        }
    }

    @Override
    public String getMessage() {
        return message != null ? message : super.getMessage();
    }
}
