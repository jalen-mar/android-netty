package com.jalen.android.netty.handler;

import java.util.List;

import io.netty.channel.ChannelHandler;

public interface HandlerFactory {
    List<ChannelHandler> getHandlers();
}
