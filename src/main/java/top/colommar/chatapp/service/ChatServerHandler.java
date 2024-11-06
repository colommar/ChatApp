package top.colommar.chatapp.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.*;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import top.colommar.chatapp.model.Message;
import top.colommar.chatapp.model.User;
import top.colommar.chatapp.repository.MessageRepository;
import top.colommar.chatapp.repository.UserRepository;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@ChannelHandler.Sharable
public class ChatServerHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {

    // 在线用户：用户名 -> Channel
    private static final Map<String, Channel> userChannels = new ConcurrentHashMap<>();

    // ChannelId -> 用户名
    private static final Map<ChannelId, String> channelUsers = new ConcurrentHashMap<>();

    // 用户状态：用户名 -> 状态（online/offline）
    private static final Map<String, String> userStatus = new ConcurrentHashMap<>();

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final UserRepository userRepository;
    private final MessageRepository messageRepository;

    public ChatServerHandler(UserRepository userRepository, MessageRepository messageRepository) {
        this.userRepository = userRepository;
        this.messageRepository = messageRepository;
        initializeUserStatus();
        log.info("ChatServerHandler created...");
    }

    /**
     * 初始化所有用户为离线状态
     */
//    @PostConstruct
    public void initializeUserStatus() {
        if(!userStatus.isEmpty()) {
            return ;
        }
        List<User> users = userRepository.findAll();
        log.warn("call func initializeUserStatus: {}", users);
        for (User user : users) {
            userStatus.put(user.getUsername(), "offline");
        }
        log.info("Initialized userStatus with all users as offline: {}", userStatus);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame frame) throws Exception {
        String json = frame.text();
        Map<String, Object> data;

        try {
            data = objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            log.error("Invalid JSON format: {}", json);
            sendError(ctx, "Invalid message format");
            return;
        }

        String type = (String) data.get("type");
        log.info("Received message of type: {}", type);

        if ("login".equals(type)) {
            handleLogin(ctx, data);
        } else if ("register".equals(type)) {
            handleRegister(ctx, data);
        } else if ("message".equals(type)) {
            handleMessage(ctx, data);
        } else if ("history".equals(type)) {
            handleHistory(ctx, data);
        } else {
            sendError(ctx, "Unsupported message type: " + type);
        }
    }

    /**
     * 处理登录请求
     */
    private void handleLogin(ChannelHandlerContext ctx, Map<String, Object> data) {
        String username = (String) data.get("username");
        String password = (String) data.get("password");
        log.warn(userStatus.toString());
        if (username == null || password == null) {
            sendLoginResponse(ctx, "failure", "用户名和密码不能为空");
            return;
        }

        log.info("Attempting login with username: {}", username);

        User user = userRepository.findByUsername(username);
        if (user != null && password.equals(user.getPassword())) { // 简化验证，直接比较密码
            // 登录成功
            userChannels.put(username, ctx.channel());
            channelUsers.put(ctx.channel().id(), username);
            userStatus.put(username, "online"); // 设置为在线

            sendLoginResponse(ctx, "success", null);

            log.warn(userStatus.toString());
            log.info("{} 登录成功", username);

            // 发送聊天历史
            try {
                sendChatHistory(ctx, username);
            } catch (Exception e) {
                log.error("Error sending chat history", e);
            }

            // 广播用户状态更新
            broadcastUserStatusUpdate(username, "online");
        } else {
            // 登录失败
            sendLoginResponse(ctx, "failure", "用户名或密码错误");
            log.info("{} 登录失败", username);
            ctx.close();
        }
    }

    /**
     * 处理注册请求
     */
    private void handleRegister(ChannelHandlerContext ctx, Map<String, Object> data) {
        String username = (String) data.get("username");
        String password = (String) data.get("password");

        if (username == null || password == null) {
            sendRegisterResponse(ctx, "failure", "用户名和密码不能为空");
            return;
        }

        log.info("Attempting registration with username: {}", username);

        if (userRepository.findByUsername(username) == null) {
            // 用户不存在，进行注册
            User newUser = new User(username, password);
            userRepository.save(newUser);
            userStatus.put(username, "offline"); // 注册后默认为离线

            sendRegisterResponse(ctx, "success", null);
            log.info("{} 注册成功", username);

            // 广播新的用户列表
            broadcastUserList();
        } else {
            // 用户已存在
            sendRegisterResponse(ctx, "failure", "用户名已存在");
            log.info("{} 注册失败，用户名已存在", username);
        }
    }

    /**
     * 处理消息发送
     */
    private void handleMessage(ChannelHandlerContext ctx, Map<String, Object> data) {
        String sender = channelUsers.get(ctx.channel().id());
        if (sender == null) {
            log.warn("未登录用户尝试发送消息");
            sendError(ctx, "未登录，无法发送消息");
            return;
        }

        String receiver = (String) data.get("receiver");
        String content = (String) data.get("content");

        if (content == null || content.trim().isEmpty()) {
            sendError(ctx, "消息内容不能为空");
            return;
        }

        // 保存消息到数据库
        Message message = new Message(sender, receiver, content, new Date());
        messageRepository.save(message);

        // 准备消息响应
        Map<String, Object> messageResponse = new HashMap<>();
        messageResponse.put("type", "message");
        messageResponse.put("sender", sender);
        messageResponse.put("content", content);
        messageResponse.put("receiver", receiver); // 可以为 null
        messageResponse.put("timestamp", message.getTimestamp().getTime()); // 时间戳

        String messageJson;
        try {
            messageJson = objectMapper.writeValueAsString(messageResponse);
        } catch (JsonProcessingException e) {
            log.error("Error serializing message", e);
            return;
        }

        TextWebSocketFrame messageFrame = new TextWebSocketFrame(messageJson);

        if (receiver != null && !receiver.isEmpty()) {
            // 私聊逻辑
            Channel receiverChannel = userChannels.get(receiver);
            if (receiverChannel != null && receiverChannel.isActive()) {
                try {
                    // 发送给接收者
                    receiverChannel.writeAndFlush(messageFrame.copy());
                    // 发送给发送者自己，确认消息已发送
                    ctx.channel().writeAndFlush(messageFrame.copy());
                    log.info("私聊消息从 {} 发送给 {}", sender, receiver);
                } catch (Exception e) {
                    log.error("Error sending private message", e);
                }
            } else {
                // 接收者不在线，发送错误信息给发送者
                sendError(ctx, "用户 " + receiver + " 不在线");
            }
        } else {
            // 群聊逻辑
            for (Map.Entry<String, Channel> entry : userChannels.entrySet()) {
                String user = entry.getKey();
                Channel channel = entry.getValue();
                if (!user.equals(sender) && channel.isActive()) {
                    try {
                        channel.writeAndFlush(messageFrame.copy());
                    } catch (Exception e) {
                        log.error("Error broadcasting message", e);
                    }
                }
            }
            // 发送给发送者自己
            ctx.channel().writeAndFlush(messageFrame.copy());
            log.info("群聊消息从 {} 发送给所有人（包括发送者）", sender);
        }
    }

    /**
     * 处理聊天历史请求
     */
    private void handleHistory(ChannelHandlerContext ctx, Map<String, Object> data) {
        Integer page = (Integer) data.get("page");
        Integer size = (Integer) data.get("size");

        if (page == null || size == null) {
            sendError(ctx, "缺少分页参数");
            return;
        }

        // 使用分页查询，假设 MessageRepository 支持分页
        List<Message> messages = messageRepository.findAllByOrderByTimestampAsc();

        // 计算分页
        int fromIndex = page * size;
        int toIndex = Math.min(fromIndex + size, messages.size());

        if (fromIndex >= messages.size()) {
            // 无更多消息
            fromIndex = toIndex = messages.size();
        }

        List<Message> pagedMessages = messages.subList(fromIndex, toIndex);

        List<Map<String, Object>> messageDataList = pagedMessages.stream().map(message -> {
            Map<String, Object> messageData = new HashMap<>();
            messageData.put("type", "message");
            messageData.put("sender", message.getSender());
            messageData.put("content", message.getContent());
            messageData.put("receiver", message.getReceiver());
            messageData.put("timestamp", message.getTimestamp().getTime());
            return messageData;
        }).collect(Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("type", "history");
        response.put("messages", messageDataList);

        try {
            String json = objectMapper.writeValueAsString(response);
            ctx.writeAndFlush(new TextWebSocketFrame(json));
        } catch (JsonProcessingException e) {
            log.error("Error serializing history response", e);
        }
    }

    /**
     * 发送登录响应
     */
    private void sendLoginResponse(ChannelHandlerContext ctx, String status, String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("type", "login");
        response.put("status", status);
        if (message != null) {
            response.put("message", message);
        }
        try {
            String json = objectMapper.writeValueAsString(response);
            ctx.writeAndFlush(new TextWebSocketFrame(json));
        } catch (JsonProcessingException e) {
            log.error("Error sending login response", e);
        }
    }

    /**
     * 发送注册响应
     */
    private void sendRegisterResponse(ChannelHandlerContext ctx, String status, String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("type", "register");
        response.put("status", status);
        if (message != null) {
            response.put("message", message);
        }
        try {
            String json = objectMapper.writeValueAsString(response);
            ctx.writeAndFlush(new TextWebSocketFrame(json));
        } catch (JsonProcessingException e) {
            log.error("Error sending register response", e);
        }
    }

    /**
     * 发送错误信息
     */
    private void sendError(ChannelHandlerContext ctx, String errorMsg) {
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("type", "error");
        errorResponse.put("message", errorMsg);
        try {
            String json = objectMapper.writeValueAsString(errorResponse);
            ctx.writeAndFlush(new TextWebSocketFrame(json));
        } catch (JsonProcessingException e) {
            log.error("Error sending error message", e);
        }
    }

    /**
     * 广播用户列表及其状态
     */
    private void broadcastUserList() {
        Map<String, Object> message = new HashMap<>();
        message.put("type", "userList");
        message.put("users", userStatus); // 包含所有用户及其状态

        String messageJson;
        try {
            messageJson = objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            log.error("Error serializing user list", e);
            return;
        }

        TextWebSocketFrame frame = new TextWebSocketFrame(messageJson);

        for (Channel channel : userChannels.values()) {
            if (channel.isActive()) {
                channel.writeAndFlush(frame.copy());
            }
        }
    }

    /**
     * 广播用户状态更新
     */
    private void broadcastUserStatusUpdate(String username, String status) {
        Map<String, Object> message = new HashMap<>();
        message.put("type", "userStatusUpdate");
//        message.put("username", username);
//        message.put("status", status);
        userStatus.put(username, status);
        message.put("users", userStatus);
        String messageJson;
        try {
            messageJson = objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            log.error("Error serializing user status update", e);
            return;
        }

        TextWebSocketFrame frame = new TextWebSocketFrame(messageJson);

        for (Channel channel : userChannels.values()) {
            if (channel.isActive()) {
                channel.writeAndFlush(frame.copy());
            }
        }
    }

    /**
     * 发送聊天历史记录
     */
    private void sendChatHistory(ChannelHandlerContext ctx, String username) throws Exception {
        List<Message> messages = messageRepository.findAllByOrderByTimestampAsc();

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

                try {
                    String messageJson = objectMapper.writeValueAsString(messageData);
                    ctx.writeAndFlush(new TextWebSocketFrame(messageJson));
                } catch (JsonProcessingException e) {
                    log.error("Error serializing chat history message", e);
                }
            }
        }
    }

    /**
     * 当连接被移除时处理
     */
    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        String username = channelUsers.remove(ctx.channel().id());
        if (username != null) {
            userChannels.remove(username);
            userStatus.put(username, "offline"); // 设置为离线
            log.info("{} 已下线", username);

            // 广播用户状态更新
            broadcastUserStatusUpdate(username, "offline");
        } else {
            log.info("一个未认证的客户端已断开连接: {}", ctx.channel().id());
        }
        super.handlerRemoved(ctx);
    }

    /**
     * 异常处理
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.error("Exception in ChatServerHandler", cause);
        ctx.close();
    }
}
