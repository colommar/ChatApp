package top.colommar.chatapp.model;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(nullable = false)
    private String password;

    // 构造函数、Getter和Setter
    public User() {}

    public User(String username, String password) {
        this.username = username;
        this.password = password;
    }
}
