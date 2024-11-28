package top.colommar.chatapp.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import top.colommar.chatapp.model.ChatFile;
import top.colommar.chatapp.repository.ChatFileRepository;
import top.colommar.chatapp.service.ChatServerHandler;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static top.colommar.chatapp.service.ChatServerHandler.userStatus;

@Slf4j
@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/chatfiles")
public class ChatFileController {

    private final ChatFileRepository chatFileRepository;
    private final ChatServerHandler chatServerHandler;

    @Autowired
    public ChatFileController(ChatFileRepository chatFileRepository, ChatServerHandler chatServerHandler) {
        this.chatFileRepository = chatFileRepository;
        this.chatServerHandler = chatServerHandler;
    }

    /**
     * 处理文件上传
     *
     * @param file     上传的文件
     * @param sender   发送者用户名
     * @param receiver 接收者用户名（可选）
     * @return ResponseEntity<String> 文件下载 URL
     */
    @PostMapping("/upload")
    public ResponseEntity<?> uploadFile(@RequestParam("file") MultipartFile file,
                                        @RequestParam("sender") String sender,
                                        @RequestParam(value = "receiver", required = false) String receiver) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("文件不能为空");
        }

        try {
            // 清理文件名
            String originalFileName = org.springframework.util.StringUtils.cleanPath(file.getOriginalFilename());

            // 生成唯一文件名
            String uniqueFileName = java.util.UUID.randomUUID().toString() + "_" + originalFileName;

            // 目标文件路径
            Path uploadDir = Paths.get("uploads").toAbsolutePath().normalize();
            Path targetLocation = uploadDir.resolve(uniqueFileName);

            // 确保上传目录存在
            java.nio.file.Files.createDirectories(uploadDir);

            // 存储文件
            file.transferTo(targetLocation.toFile());

            // 创建文件记录
            ChatFile chatFile = new ChatFile();
            chatFile.setFileName(originalFileName);
            chatFile.setFilePath("uploads/" + uniqueFileName);
            chatFile.setSender(sender);
            chatFile.setReceiver(receiver != null && !receiver.equals("null") ? receiver : "");
            chatFile.setTimestamp(System.currentTimeMillis());

            // 保存到数据库
            ChatFile savedChatFile = chatFileRepository.save(chatFile);

            // 生成文件信息的 JSON 响应
            Map<String, Object> fileResponse = new HashMap<>();
            fileResponse.put("id", savedChatFile.getId());
            fileResponse.put("fileName", savedChatFile.getFileName());
            fileResponse.put("filePath", savedChatFile.getFilePath());
            fileResponse.put("sender", savedChatFile.getSender());
            fileResponse.put("receiver", savedChatFile.getReceiver());
            fileResponse.put("timestamp", savedChatFile.getTimestamp());

            // 通过 WebSocket 广播文件消息
//            chatServerHandler.broadcastFileMessage(savedChatFile);
            for (Map.Entry<String, String> entry : userStatus.entrySet()) {
                String key = entry.getKey();    // 获取键
                chatServerHandler.broadcastFileList(key);
            }

            // 返回文件信息的 JSON 响应
            return ResponseEntity.ok(fileResponse);

        } catch (IOException e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("文件上传失败");
        }
    }


    /**
     * 处理文件下载
     *
     * @param id 文件 ID
     * @return ResponseEntity<FileSystemResource>
     */
    @GetMapping("/download/{id}")
    public ResponseEntity<FileSystemResource> downloadFile(@PathVariable Long id) {

        log.warn(String.valueOf(id));

        Optional<ChatFile> optionalFile = chatFileRepository.findById(id);
        if (!optionalFile.isPresent()) {
            return ResponseEntity.notFound().build();
        }

        ChatFile chatFile = optionalFile.get();

        log.warn(String.valueOf(chatFile.toString()));

        Path filePath = Paths.get(chatFile.getFilePath());
        log.warn(String.valueOf(filePath));
        File file = filePath.toFile();

        if (!file.exists()) {
            return ResponseEntity.notFound().build();
        }

        FileSystemResource resource = new FileSystemResource(file);

        // 设置下载的原始文件名
        String originalFileName = extractOriginalFileName(file.getName());


        // 2024-11-26 17:46:42 [http-nio-8080-exec-1] WARN  o.a.coyote.http11.Http11Processor - The HTTP response header [Content-Disposition] with value [attachment; filename="参与协议书.pdf"] has been removed from the response because it is invalid
        // [Content-Disposition] warn
//        return ResponseEntity.ok()
//                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + originalFileName + "\"")
//                .contentLength(file.length())
//                .contentType(MediaType.APPLICATION_OCTET_STREAM)
//                .body(resource);
        // 指明为 UTF-8
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + URLEncoder.encode(originalFileName, StandardCharsets.UTF_8))
                .contentLength(file.length())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);

    }

    /**
     * 获取所有文件历史记录
     *
     * @return ResponseEntity<List<ChatFile>>
     */
    @GetMapping("/history/{username}")
    public ResponseEntity<List<ChatFile>> getFileHistory(@PathVariable String username) {
        List<ChatFile> files = chatFileRepository.findBySenderOrReceiver(username, username);
        return ResponseEntity.ok(files);
    }

    /**
     * 提取原始文件名从唯一文件名
     *
     * @param uniqueFileName 唯一文件名（UUID_OriginalName）
     * @return 原始文件名
     */
    private String extractOriginalFileName(String uniqueFileName) {
        if (uniqueFileName == null) return "";
        int index = uniqueFileName.indexOf('_');
        if (index == -1) return uniqueFileName; // 如果没有下划线，返回整个字符串
        return uniqueFileName.substring(index + 1);
    }
}
