package com.appad.services;

import com.appad.models.User;
import com.appad.models.ArtistMembership;
import com.appad.repository.ArtistMembershipRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class TaskSchedulerService {

    @Autowired
    private ArtistMembershipRepository membershipRepository;

    @Autowired
    private com.appad.services.RevenueService revenueService;

    @Autowired
    private com.appad.repository.UserRepository userRepository;

    @Autowired
    private com.appad.services.NotificationService notificationService;

    // Chạy mỗi 5 phút để kiểm tra hội viên hết hạn
    @Scheduled(cron = "0 */5 * * * *")
    public void checkExpiredMemberships() {
        System.out.println("Cron Job: Đang kiểm tra hội viên hết hạn...");
        List<ArtistMembership> expired = membershipRepository.findByExpiryDateBeforeAndStatus(LocalDateTime.now(), "active");
        for (ArtistMembership m : expired) {
            m.setStatus("expired");
            membershipRepository.save(m);
        }
        System.out.println("Cron Job: Đã cập nhật " + expired.size() + " hội viên hết hạn.");
    }

    // Chạy mỗi 5 phút để tổng kết doanh thu tháng trước
    @Scheduled(cron = "0 */5 * * * *")
    public void monthlyRevenueReport() {
        System.out.println("Cron Job: Đang tạo báo cáo doanh thu tháng...");
        LocalDate lastMonth = LocalDate.now().minusMonths(1);
        revenueService.calculateMonthlyRevenue(lastMonth.getYear(), lastMonth.getMonthValue());
    }

    // Chạy mỗi 5 phút để thông báo Premium sắp hết hạn (3 ngày trước)
    @Scheduled(cron = "0 */5 * * * *")
    public void checkExpiringPremium() {
        System.out.println("Cron Job: Đang kiểm tra Premium sắp hết hạn...");
        LocalDateTime threeDaysLater = LocalDateTime.now().plusDays(3);
        
        // Simple logic: get all premium users and check expiry
        userRepository.findAll().stream()
                .filter(u -> u.getIsPremium() != null && u.getIsPremium() == 1)
                .filter(u -> u.getPremiumExpiry() != null && u.getPremiumExpiry().isBefore(threeDaysLater) && u.getPremiumExpiry().isAfter(LocalDateTime.now()))
                .forEach(u -> {
                    notificationService.createNotification(u, 
                            "Premium sắp hết hạn", 
                            "Gói Premium của bạn sẽ hết hạn vào " + u.getPremiumExpiry() + ". Hãy gia hạn ngay!", 
                            "premium_expiring", 
                            Map.of("expiry", u.getPremiumExpiry()));
                });
    }

    // Chạy mỗi 5 phút để tự động cập nhật trạng thái người dùng hết hạn Premium
    @Scheduled(cron = "0 */5 * * * *")
    public void updateExpiredPremiumUsers() {
        System.out.println("Cron Job: Đang kiểm tra người dùng Premium hết hạn...");
        List<User> expiredUsers = userRepository.findByIsPremiumAndPremiumExpiryBefore(1, LocalDateTime.now());
        if (!expiredUsers.isEmpty()) {
            for (User u : expiredUsers) {
                u.setIsPremium(0);
                userRepository.save(u);
            }
            System.out.println("Cron Job: Đã cập nhật " + expiredUsers.size() + " tài khoản Premium hết hạn về thường.");
        } else {
            System.out.println("Cron Job: Không có tài khoản Premium nào hết hạn cần cập nhật.");
        }
    }
}
