package com.jalen.android.netty.handler;

import android.util.ArrayMap;
import android.util.Log;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.jalen.android.bootstrap.reflect.MethodUtil;
import com.jalen.android.netty.exception.HttpException;
import com.jalen.android.netty.exception.HttpServerException;
import com.jalen.android.netty.util.HttpControllerHandler;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.reflect.Method;
import java.net.FileNameMap;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelProgressiveFuture;
import io.netty.channel.ChannelProgressiveFutureListener;
import io.netty.channel.DefaultFileRegion;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.multipart.Attribute;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
import io.netty.handler.codec.http.multipart.InterfaceHttpData;
import io.netty.util.CharsetUtil;

@ChannelHandler.Sharable
public class HttpMessageHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest req) {
        if (HttpUtil.is100ContinueExpected(req)) {
            ctx.write(new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.CONTINUE));
        }

        Object content;
        String errorMessage = "No Error!";
        HttpResponseStatus status = HttpResponseStatus.OK;
        try {
            content = handleRequest(req);
        } catch (HttpException e) {
            switch (e.getCode()) {
                case 404 :
                    status = HttpResponseStatus.NOT_FOUND;
                    break;
                case 500 :
                    status = HttpResponseStatus.INTERNAL_SERVER_ERROR;
                    break;
            }
            content = errorMessage = e.getMessage();
        }

        if (content == null || content instanceof String || content instanceof Boolean || content instanceof Number || content instanceof Character) {
            sendMessage(ctx, req, status, content == null ? null : content.toString(), errorMessage);
            return;
        }

        if (content instanceof File) {
            sendFile(ctx, req, status, (File) content);
            return;
        }

        sendMessage(ctx, req, status, JSON.toJSONString(content), errorMessage);
    }

    private void sendFile(ChannelHandlerContext ctx, FullHttpRequest req, HttpResponseStatus status, File content) {
        try {
            RandomAccessFile randomAccessFile = new RandomAccessFile(content, "r");
            FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status);
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, getMimeType(content));
            response.headers().set("Error-Message", "No Error!");
            response.headers().set(HttpHeaderNames.ACCEPT_CHARSET, CharsetUtil.UTF_8.toString());
            response.headers().set(HttpHeaderNames.CONTENT_LENGTH, randomAccessFile.length());
            response.headers().add(HttpHeaderNames.CONTENT_DISPOSITION, String.format("attachment; filename=\"%s\"", content.getName()));
            ctx.write(response);
            ChannelFuture sendFileFuture = ctx.write(new DefaultFileRegion(randomAccessFile.getChannel(), 0, randomAccessFile.length()), ctx.newProgressivePromise());
            sendFileFuture.addListener(new ChannelProgressiveFutureListener() {
                @Override
                public void operationComplete(ChannelProgressiveFuture future) throws IOException {
                    randomAccessFile.close();
                }

                @Override
                public void operationProgressed(ChannelProgressiveFuture future, long progress, long total) {
                    Log.i("AASSDD", progress + " : " + total);
                }
            });
            ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
        } catch (Exception e) {
            sendMessage(ctx, req, HttpResponseStatus.INTERNAL_SERVER_ERROR, e.getMessage(), e.getMessage());
        }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        super.channelReadComplete(ctx);
        ctx.flush();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        super.exceptionCaught(ctx, cause);
    }

    private String getMimeType(File file) {
        FileNameMap fileNameMap = URLConnection.getFileNameMap();
        String type = fileNameMap.getContentTypeFor(file.getName());
        return type == null || type.length() == 0 ? "application/octet-stream" : type;
    }


    private void sendMessage(ChannelHandlerContext ctx, FullHttpRequest req, HttpResponseStatus status, String content, String errorMessage) {
        FullHttpResponse response;
        Charset charset = HttpUtil.getCharset(req, CharsetUtil.UTF_8);
        if (content == null) {
            response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status);
        } else {
            response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, Unpooled.copiedBuffer(content, charset));
        }
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, charset.toString());
        response.headers().set("Error-Message", errorMessage);
        response.headers().set(HttpHeaderNames.ACCEPT_CHARSET, CharsetUtil.UTF_8.toString());
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
        if (HttpUtil.isKeepAlive(req)) {
            response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
            ctx.writeAndFlush(response);
            ctx.fireChannelRead(req);
        } else {
            ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
        }
    }

    private Object handleRequest(FullHttpRequest req) {
        Action action = HttpControllerHandler.findController(req.uri(), req.method().toString());
        if (action != null) {
            try {
                Object attributes = findAttributes(req);
                if ((action.actionMethod).getParameterTypes().length == 0) {
                    return MethodUtil.invoke(action.actionObject, action.actionMethod);
                } else {
                    return MethodUtil.invoke(action.actionObject, action.actionMethod, attributes);
                }
            } catch (Exception e) {
                if (e instanceof HttpException) {
                    throw (HttpException) e;
                }
                throw new HttpServerException(String.format("服务器异常,具体信息(%s).", e.getMessage()));
            }
        } else {
            throw new HttpException(404, "抱歉，您访问的资源不存在.");
        }
    }

    private Object findAttributes(FullHttpRequest request) throws IOException {
        HttpMethod method = request.method();
        ArrayMap<String, Object> params = new ArrayMap<>();
        if (HttpMethod.GET == method) {
            QueryStringDecoder decoder = new QueryStringDecoder(request.uri());
            Iterator<Map.Entry<String, List<String>>> iterator = decoder.parameters().entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, List<String>> entry = iterator.next();
                params.put(entry.getKey(), entry.getValue().get(0));
            }
        } else if (HttpMethod.POST == method) {
            String contentType = request.headers().get("Content-Type");
            contentType = contentType == null ? "" : contentType;
            if (contentType.contains("application/json")) {
               params.put("json", JSON.parse(request.content().toString(CharsetUtil.UTF_8)));
            } else {
                HttpPostRequestDecoder decoder = new HttpPostRequestDecoder(request);
                List<InterfaceHttpData> paramArray = decoder.getBodyHttpDatas();
                for (InterfaceHttpData parameter : paramArray) {
                    Attribute data = (Attribute) parameter;
                    params.put(data.getName(), data.getValue());
                }
            }
        } else {
            throw new HttpException(404, "抱歉，您访问的资源不存在.");
        }
        return params.size() == 1 ? params.get(params.keyAt(0)) : params;
    }

    public static class Action {
        public Object actionObject;
        public Method actionMethod;

        public Action(Object actionObject, Method actionMethod) {
            this.actionObject = actionObject;
            this.actionMethod = actionMethod;
        }
    }
}
