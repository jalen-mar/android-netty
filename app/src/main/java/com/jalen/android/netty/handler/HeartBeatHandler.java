package com.jalen.android.netty.handler;

import java.util.concurrent.TimeUnit;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleStateHandler;

@ChannelHandler.Sharable
public class HeartBeatHandler extends IdleStateHandler {
    private final ChannelInboundHandlerAdapter timeoutHander;

    public HeartBeatHandler(int timeout) {
        super(0, 0, timeout, TimeUnit.MILLISECONDS);
        timeoutHander = new AcceptorIdleStateTrigger();
    }

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        super.channelRegistered(ctx);
        ctx.channel().pipeline().addAfter(toString(), timeoutHander.toString(), timeoutHander);
    }

    @Override
    public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
        ctx.channel().pipeline().remove(timeoutHander.toString());
        super.channelUnregistered(ctx);
    }
}
