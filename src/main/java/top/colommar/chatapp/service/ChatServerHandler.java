package top.colommar.chatapp.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.*;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import lombok.extern.slf4j.Slf4j;
import top.colommar.chatapp.model.Message;
import top.colommar.chatapp.model.User;
import top.colommar.chatapp.repository.MessageRepository;
import top.colommar.chatapp.repository.UserRepository;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class ChatServerHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {

    private static Map<String, Channel> userChannels = new ConcurrentHashMap<>();
    private static Map<ChannelId, String> channelUsers = new ConcurrentHashMap<>();
    private static ObjectMapper objectMapper = new ObjectMapper();

    private final UserRepository userRepository;
    private final MessageRepository messageRepository;

    public ChatServerHandler(UserRepository userRepository, MessageRepository messageRepository) {
        this.userRepository = userRepository;
        this.messageRepository = messageRepository;
        log.info("ChatServerHandler created...");
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame frame) throws Exception {
        String json = frame.text();
        Map<String, Object> data = objectMapper.readValue(json, Map.class);
        String type = (String) data.get("type");
        log.info("Received message of type: {}", type);

        if ("login".equals(type)) {
            handleLogin(ctx, data);
        } else if ("message".equals(type)) {
            handleMessage(ctx, data);
        } else {
            String msg = "Unsupported message type: " + type;
            ctx.writeAndFlush(new TextWebSocketFrame(msg));
        }
    }

    private void handleLogin(ChannelHandlerContext ctx, Map<String, Object> data) {
        String username = (String) data.get("username");
        String password = (String) data.get("password");
        log.info("Attempting login with username: {}", username);

        User user = userRepository.findByUsername(username);
        if (user != null && user.getPassword().equals(password)) {
            userChannels.put(username, ctx.channel());
            channelUsers.put(ctx.channel().id(), username);
            Map<String, Object> response = new HashMap<>();
            response.put("type", "login");
            response.put("status", "success");
            try {
                ctx.writeAndFlush(new TextWebSocketFrame(objectMapper.writeValueAsString(response)));
            } catch (JsonProcessingException e) {
                log.error("Error sending login success message", e);
            }
            log.info("{} 登录成功", username);
            // 发送历史记录
            try {
                sendChatHistory(ctx, username);
            } catch (Exception e) {
                log.error("Error sending chat history", e);
            }
        } else {
            Map<String, Object> response = new HashMap<>();
            response.put("type", "login");
            response.put("status", "failure");
            response.put("message", "用户名或密码错误");
            try {
                ctx.writeAndFlush(new TextWebSocketFrame(objectMapper.writeValueAsString(response)));
            } catch (JsonProcessingException e) {
                log.error("Error sending login failure message", e);
            }
            log.info("{} 登录失败", username);
            ctx.close();
        }
    }

    private void handleMessage(ChannelHandlerContext ctx, Map<String, Object> data) {
        String sender = channelUsers.get(ctx.channel().id());
        if (sender == null) {
            log.warn("未登录用户尝试发送消息");
            return;
        }
        String receiver = (String) data.get("receiver");
        String content = (String) data.get("content");

        Message message = new Message(sender, receiver, content, new Date());
        messageRepository.save(message);

        Map<String, Object> messageResponse = new HashMap<>();
        messageResponse.put("type", "message");
        messageResponse.put("sender", sender);
        messageResponse.put("content", content);
        messageResponse.put("receiver", receiver); // 可以为 null
        messageResponse.put("timestamp", new Date().getTime()); // 使用当前时间的时间戳

        if (receiver != null && !receiver.isEmpty()) {
            // **私聊逻辑**
            Channel receiverChannel = userChannels.get(receiver);
            if (receiverChannel != null) {
                try {
                    // 仅发送给接收者
                    receiverChannel.writeAndFlush(new TextWebSocketFrame(objectMapper.writeValueAsString(messageResponse)));
                    log.info("私聊消息从 {} 发送给 {}", sender, receiver);
                } catch (JsonProcessingException e) {
                    log.error("Error sending private message", e);
                }
            } else {
                // 接收者不在线，发送错误信息给发送者
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("type", "error");
                errorResponse.put("message", "用户 " + receiver + " 不在线");
                try {
                    ctx.writeAndFlush(new TextWebSocketFrame(objectMapper.writeValueAsString(errorResponse)));
                } catch (JsonProcessingException e) {
                    log.error("Error sending error message", e);
                }
            }
        } else {
            // **群聊逻辑**
            for (Channel channel : userChannels.values()) {
                if (channel != ctx.channel()) { // 不包括发送者
                    try {
                        channel.writeAndFlush(new TextWebSocketFrame(objectMapper.writeValueAsString(messageResponse)));
                    } catch (JsonProcessingException e) {
                        log.error("Error broadcasting message", e);
                    }
                }
            }
            log.info("群聊消息从 {} 发送给所有人（除了发送者）", sender);
        }
    }

    private void sendChatHistory(ChannelHandlerContext ctx, String username) throws Exception {

        /*{"type":"login","username":"bob","password":"123"}	50
        17:06:12.836
        {"type":"login","status":"success"}	35
        17:06:12.840
        {"receiver":"bob","sender":"alice","content":"Hello Bob!","timestamp":1730873070000}	84
        17:06:12.848
        {"receiver":"alice","sender":"bob","content":"Hi Alice!","timestamp":1730873070000}	83
        17:06:12.848
        {"receiver":null,"sender":"charlie","content":"Hello everyone!","timestamp":1730873070000}	90
        17:06:12.848
        {"receiver":"bob","sender":"alice","content":"Are you coming to the meeting?","timestamp":1696122000000}	104
        17:06:12.848
        {"receiver":"alice","sender":"bob","content":"Yes, I will be there.","timestamp":1696122300000}	95
        17:06:12.848
        {"receiver":null,"sender":"charlie","content":"Good morning everyone!","timestamp":1696122600000}	97
        17:06:12.848*/

        List<Message> messages = messageRepository.findAllByOrderByTimestampAsc();;

        for (Message message : messages) {
            String receiver = message.getReceiver();
            String content = message.getContent();
            String sender = message.getSender();
            Date date = message.getTimestamp();

            // 判断消息是否为用户可见
            if (receiver == null || sender.equals(username) || username.equals(receiver)) {
                Map<String, Object> messageData = new HashMap<>();
                messageData.put("type", "message");
                messageData.put("sender", sender);
                messageData.put("content", content);
                messageData.put("timestamp", date.getTime()); // 使用时间戳
                messageData.put("receiver", receiver); // 可以为 null
                // 发送消息给客户端
                ctx.writeAndFlush(new TextWebSocketFrame(objectMapper.writeValueAsString(messageData)));
            }
        }
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        String username = channelUsers.remove(ctx.channel().id());
        if (username != null) {
            userChannels.remove(username);
            log.info("{} 已下线", username);
        }
        super.handlerRemoved(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.error("Exception in ChatServerHandler", cause);
        ctx.close();
    }
}
