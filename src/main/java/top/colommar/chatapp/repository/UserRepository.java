package top.colommar.chatapp.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.colommar.chatapp.model.User;

public interface UserRepository extends JpaRepository<User, Integer> {
    User findByUsername(String username);
}
