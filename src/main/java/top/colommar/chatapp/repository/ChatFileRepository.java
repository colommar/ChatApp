package top.colommar.chatapp.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import top.colommar.chatapp.model.ChatFile;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatFileRepository extends JpaRepository<ChatFile, Long> {

    /**
     * 根据文件路径查找 ChatFile
     *
     * @param filePath 文件存储路径
     * @return Optional<ChatFile>
     */
    Optional<ChatFile> findByFilePath(String filePath);

    /**
     * 根据发送者或接收者查找 ChatFiles
     *
     * @param sender 发送
     * @param receiver 收取
     * @return List<ChatFile>
     */
    List<ChatFile> findBySenderOrReceiver(String sender, String receiver);
}
