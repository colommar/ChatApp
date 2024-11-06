// 添加一个控制器类，例如 LoginController.java
package top.colommar.chatapp.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import top.colommar.chatapp.model.User;
import top.colommar.chatapp.repository.UserRepository;

import java.util.HashMap;
import java.util.Map;

@RestController
@CrossOrigin(origins = "*")
public class LoginController {

    @Autowired
    private UserRepository userRepository;

    @PostMapping("/login")
    public Map<String, String> login(@RequestBody Map<String, String> data) {
        String username = data.get("username");
        String password = data.get("password");
        User user = userRepository.findByUsername(username);
        Map<String, String> response = new HashMap<>();
        if (user != null && user.getPassword().equals(password)) {
            response.put("status", "success");
        } else {
            response.put("status", "failure");
            response.put("message", "用户名或密码错误");
        }
        return response;
    }
}
