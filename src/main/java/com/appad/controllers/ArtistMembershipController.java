package com.appad.controllers;

import com.appad.models.Artist;
import com.appad.models.ArtistMembership;
import com.appad.models.User;
import com.appad.repository.ArtistMembershipRepository;
import com.appad.repository.ArtistRepository;
import com.appad.repository.UserRepository;
import com.appad.repository.TransactionRepository;
import com.appad.models.Transaction;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/memberships")
@RequiredArgsConstructor
public class ArtistMembershipController {
    private final ArtistMembershipRepository membershipRepository;
    private final ArtistRepository artistRepository;
    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final com.appad.services.NotificationService notificationService;

    @PostMapping("/subscribe")
    @Transactional
    public ResponseEntity<?> subscribe(@RequestBody Map<String, Object> payload) {
        Integer userId = Double.valueOf(payload.get("userId").toString()).intValue();
        Integer artistId = Double.valueOf(payload.get("artistId").toString()).intValue();
        
        User user = userRepository.findById(userId).orElseThrow();
        Artist artist = artistRepository.findById(artistId).orElseThrow();

        double cost = (artist.getMembershipPrice() != null) ? artist.getMembershipPrice() : 50000.0;
        int durationDays = (artist.getMembershipDurationDays() != null) ? artist.getMembershipDurationDays() : 30;

        if (user.getBalance() < cost) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false, 
                "message", "Số dư không đủ. Cần " + cost + ", hiện có " + user.getBalance(),
                "balance", user.getBalance(),
                "required", cost
            ));
        }

        // Trừ tiền user
        user.setBalance(user.getBalance() - cost);
        userRepository.save(user);

        // Cộng tiền cho artist (giả sử artist nhận 80%)
        double currentWallet = artist.getWalletBalance() != null ? artist.getWalletBalance() : 0.0;
        artist.setWalletBalance(currentWallet + (cost * 0.8));
        artistRepository.save(artist);

        // Lưu membership
        ArtistMembership membership = new ArtistMembership();
        membership.setUser(user);
        membership.setUserId(userId);
        membership.setArtist(artist);
        membership.setArtistId(artistId);
        membership.setPricePaid(cost);
        membership.setStartDate(LocalDateTime.now());
        membership.setExpiryDate(LocalDateTime.now().plusDays(durationDays));
        membershipRepository.save(membership);

        // Lưu transaction
        transactionRepository.save(Transaction.builder()
                .userId(userId)
                .targetId(artistId.longValue())
                .type("membership")
                .amount(cost)
                .status("completed")
                .build());

        // Notify User
        notificationService.createNotification(user, 
                "Đã tham gia Fan Club", 
                "Bạn đã trở thành hội viên của nghệ sĩ \"" + artist.getName() + "\".", 
                "spend", 
                Map.of("artistId", artistId));

        // Notify Artist
        if (artist.getUserId() != null) {
            userRepository.findById(artist.getUserId()).ifPresent(artistUser -> {
                notificationService.createNotification(artistUser,
                        "Hội viên mới",
                        user.getUsername() + " vừa tham gia gói thành viên của bạn.",
                        "revenue",
                        Map.of("userId", userId, "amount", cost * 0.8));
            });
        }

        return ResponseEntity.ok(Map.of("success", true, "message", "Đã tham gia gói thành viên của nghệ sĩ!"));
    }

    @GetMapping("/check")
    public ResponseEntity<?> checkMembership(@RequestParam Integer userId, @RequestParam Integer artistId) {
        boolean active = membershipRepository.findByUserUserIdAndArtistArtistId(userId, artistId)
                .stream()
                .anyMatch(m -> "active".equals(m.getStatus()) && m.getExpiryDate() != null && m.getExpiryDate().isAfter(LocalDateTime.now()));
        return ResponseEntity.ok(Map.of("success", true, "active", active));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<?> getMembershipsByUserId(@PathVariable Integer userId) {
        return ResponseEntity.ok(Map.of("success", true, "data", membershipRepository.findByUserId(userId)));
    }
}
