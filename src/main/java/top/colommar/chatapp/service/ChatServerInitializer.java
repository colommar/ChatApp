package top.colommar.chatapp.service;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import top.colommar.chatapp.repository.MessageRepository;
import top.colommar.chatapp.repository.UserRepository;

public class ChatServerInitializer extends ChannelInitializer<SocketChannel> {

    private final UserRepository userRepository;
    private final MessageRepository messageRepository;

    public ChatServerInitializer(UserRepository userRepository, MessageRepository messageRepository) {
        this.userRepository = userRepository;
        this.messageRepository = messageRepository;
    }

    @Override
    protected void initChannel(SocketChannel socketChannel) throws Exception {
        socketChannel.pipeline()
                .addLast(new HttpServerCodec())
                .addLast(new HttpObjectAggregator(65536))
                .addLast(new WebSocketServerProtocolHandler("/chat")) // 使用 Netty 提供的处理器
                .addLast(new ChatServerHandler(userRepository, messageRepository)); // 业务处理器
    }
}
