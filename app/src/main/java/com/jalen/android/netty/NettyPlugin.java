package com.jalen.android.netty;

import com.jalen.android.bootstrap.annotation.Initialize;
import com.jalen.android.bootstrap.beans.BeanFactory;
import com.jalen.android.netty.annotation.EnableNetty;
import com.jalen.android.netty.handler.HandlerFactory;

import java.util.ArrayList;
import java.util.List;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;

public class NettyPlugin {
    public static String BEAN_NAME = "com.jalen.android.netty:";
    private ServerBootstrap server = null;
    private EventLoopGroup acceptGroup = null;
    private EventLoopGroup workerGroup = null;
    private List<ChannelFuture> futures;

    @Initialize
    public void init(EnableNetty config) throws InterruptedException {
        acceptGroup = new NioEventLoopGroup(config.port().length);
        workerGroup = new NioEventLoopGroup(config.workerCapacity());
        server = new ServerBootstrap()
                .group(acceptGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_BACKLOG, config.maxCapacity())
                .option(ChannelOption.SO_RCVBUF, config.readBuffer() == 0 ? null : config.readBuffer())
                .option(ChannelOption.SO_SNDBUF, config.writeBuffer() == 0 ? null : config.writeBuffer())
                .option(ChannelOption.SO_KEEPALIVE, config.keepAlive())
                .option(ChannelOption.TCP_NODELAY, config.enableNagle()).childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel channel) {
                        int port = channel.localAddress().getPort();
                        HandlerFactory factory = BeanFactory.getInstance().getBean(BEAN_NAME + port);
                        if (factory != null) {
                            List<ChannelHandler> handlers = factory.getHandlers();
                            ChannelPipeline pipeline = channel.pipeline();
                            for (ChannelHandler handler : handlers) {
                                pipeline.addLast(handler.toString(), handler);
                            }
                        }
                    }
                });
        futures = new ArrayList<>();
        for (int port : config.port()) {
            ChannelFuture future = server.bind(port).sync();
            future.channel().closeFuture().addListener(ChannelFutureListener.CLOSE);
            futures.add(future);
        }
    }

    public void destroy() {
        for (ChannelFuture future : futures) {
            try {
                future.channel().close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        acceptGroup.shutdownGracefully();
        workerGroup.shutdownGracefully();
    }
}
