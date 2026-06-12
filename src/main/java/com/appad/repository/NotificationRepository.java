package com.appad.repository;

import com.appad.models.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByUserUserIdOrderByCreatedAtDesc(Long userId);
    List<Notification> findByUserUserIdAndIsRead(Long userId, boolean isRead);
    void deleteAllByUserUserId(Long userId);
}
