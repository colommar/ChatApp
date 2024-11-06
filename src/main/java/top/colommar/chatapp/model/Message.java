package top.colommar.chatapp.model;

import jakarta.persistence.*;
import lombok.Data;

import java.util.Date;


@Data
@Entity
public class Message {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable=false)
    private String sender;

    @Column
    private String receiver; // null表示群聊

    @Column(nullable=false)
    private String content;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(nullable=false)
    private Date timestamp;

    // 构造函数、Getter和Setter
    public Message() {}

    public Message(String sender, String receiver, String content, Date timestamp) {
        this.sender = sender;
        this.receiver = receiver;
        this.content = content;
        this.timestamp = timestamp;
    }

    // Getters and Setters
    // ...
}

