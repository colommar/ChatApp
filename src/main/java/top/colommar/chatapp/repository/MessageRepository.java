package top.colommar.chatapp.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.colommar.chatapp.model.Message;

import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {

    List<Message> findTop50ByOrderByTimestampAsc();

    List<Message> findAllByOrderByTimestampAsc();
}
