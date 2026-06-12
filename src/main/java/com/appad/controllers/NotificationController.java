package com.appad.controllers;

import com.appad.models.Notification;
import com.appad.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {
    
    private final NotificationRepository notificationRepository;
    private final com.appad.services.NotificationService notificationService;
    private final com.appad.repository.UserRepository userRepository;

    @GetMapping
    public ResponseEntity<?> getMyNotifications(@org.springframework.security.core.annotation.AuthenticationPrincipal Integer authUserId) {
        if (authUserId == null) return ResponseEntity.status(401).build();
        List<Notification> notifications = notificationRepository.findByUserUserIdOrderByCreatedAtDesc(authUserId.longValue());
        long unreadCount = notifications.stream().filter(n -> !n.isRead()).count();
        return ResponseEntity.ok(Map.of("success", true, "data", Map.of(
            "notifications", notifications,
            "unread_count", unreadCount
        )));
    }

    @PostMapping("/mark-read/{id}")
    public ResponseEntity<?> markAsRead(@PathVariable Long id) {
        notificationRepository.findById(id).ifPresent(notification -> {
            notification.setRead(true);
            notificationRepository.save(notification);
        });
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/mark-all-read")
    public ResponseEntity<?> markAllRead(@org.springframework.security.core.annotation.AuthenticationPrincipal Integer authUserId) {
        if (authUserId == null) return ResponseEntity.status(401).build();
        List<Notification> unread = notificationRepository.findByUserUserIdAndIsRead(authUserId.longValue(), false);
        for (Notification n : unread) {
            n.setRead(true);
            notificationRepository.save(n);
        }
        return ResponseEntity.ok(Map.of("success", true));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteNotification(@PathVariable Long id) {
        notificationRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @DeleteMapping("/delete-all")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<?> deleteAll(@org.springframework.security.core.annotation.AuthenticationPrincipal Integer authUserId) {
        if (authUserId == null) return ResponseEntity.status(401).build();
        notificationRepository.deleteAllByUserUserId(authUserId.longValue());
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/broadcast")
    public ResponseEntity<?> broadcast(@org.springframework.security.core.annotation.AuthenticationPrincipal Integer authUserId,
                                      @RequestBody Map<String, String> payload) {
        com.appad.models.User user = userRepository.findById(authUserId).orElseThrow();
        if (!"admin".equals(user.getRole())) {
            return ResponseEntity.status(403).body(Map.of("success", false, "message", "Only admin can broadcast"));
        }

        String title = payload.get("title");
        String message = payload.get("message");
        notificationService.broadcast(title, message, "system", null);
        return ResponseEntity.ok(Map.of("success", true));
    }
}
