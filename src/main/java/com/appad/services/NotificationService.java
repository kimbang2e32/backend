package com.appad.services;

import com.appad.models.Notification;
import com.appad.models.User;
import com.appad.repository.NotificationRepository;
import com.appad.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class NotificationService {
    
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public void createNotification(User user, String title, String message, String type, Map<String, Object> data) {
        Notification notification = new Notification();
        notification.setUser(user);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setType(type);
        
        if (data != null) {
            try {
                notification.setData(objectMapper.writeValueAsString(data));
            } catch (Exception e) {
                notification.setData(null);
            }
        }
        
        notificationRepository.save(notification);
    }

    public void sendToAllAdmins(String title, String message, String type, Map<String, Object> data) {
        List<User> admins = userRepository.findByRole("admin");
        for (User admin : admins) {
            createNotification(admin, title, message, type, data);
        }
    }

    public void broadcast(String title, String message, String type, Map<String, Object> data) {
        List<User> users = userRepository.findAll();
        for (User user : users) {
             createNotification(user, title, message, type, data);
        }
    }
}
