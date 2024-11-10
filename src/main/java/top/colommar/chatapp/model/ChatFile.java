package top.colommar.chatapp.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


/**
 * 注意：fileName和filePath不能用String，不然会取不出来
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "chatfile")
public class ChatFile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(nullable = false)
    private String fileName;

    @Column(nullable = false)
    private String filePath;

    private String sender;

    private String receiver;

    private long timestamp; // 添加时间戳字段，便于排序和展示
}
