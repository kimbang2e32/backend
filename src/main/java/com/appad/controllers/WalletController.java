package com.appad.controllers;

import com.appad.models.Transaction;
import com.appad.models.User;
import com.appad.models.PurchasedSong;
import com.appad.models.PurchasedAlbum;
import com.appad.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Map;

@RestController
@RequestMapping("/api/wallet")
@RequiredArgsConstructor
public class WalletController {
    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final SongRepository songRepository;
    private final PurchasedSongRepository purchasedSongRepository;
    private final AlbumRepository albumRepository;
    private final PurchasedAlbumRepository purchasedAlbumRepository;
    private final com.appad.services.SongService songService;
    private final com.appad.services.NotificationService notificationService;
    private final ArtistRepository artistRepository;

    private Integer getCurrentUserId() {
        return (Integer) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    @GetMapping("/balance")
    public ResponseEntity<?> getBalance() {
        Integer userId = getCurrentUserId();
        return userRepository.findById(userId)
                .map(user -> ResponseEntity.ok(Map.of("success", true, "data", Map.of("balance", user.getBalance()))))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/topup")
    public ResponseEntity<?> topupPreview(@RequestBody Map<String, Object> payload) {
        Double amount = Double.valueOf(payload.get("amount").toString());

        if (amount <= 0 || amount > 10000000) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Số tiền không hợp lệ"));
        }

        // Just generate preview info without saving to DB yet
        // We use a temporary reference code based on timestamp + userId to make it unique for the QR
        Integer userId = getCurrentUserId();
        String referenceCode = "NAP" + userId + System.currentTimeMillis() % 1000000;
        
        Map<String, Object> bankInfo = Map.of(
            "bank_name", "Vietcombank",
            "account_number", "1029727303",
            "account_name", "NGUYEN SY KIM BANG",
            "qr_url", "https://img.vietqr.io/image/VCB-1029727303-compact2.png?amount=" + amount.intValue() + "&addInfo=" + referenceCode + "&accountName=NGUYEN%20SY%20KIM%20BANG"
        );

        return ResponseEntity.ok(Map.of(
            "success", true,
            "data", Map.of(
                "amount", amount,
                "reference_code", referenceCode,
                "bank_info", bankInfo
            )
        ));
    }

    @PostMapping("/confirm")
    @Transactional
    public ResponseEntity<?> confirmTopup(@RequestBody Map<String, Object> payload) {
        Integer userId = getCurrentUserId();
        Double amount = Double.valueOf(payload.get("amount").toString());
        String referenceCode = payload.get("reference_code").toString();

        Transaction transaction = Transaction.builder()
                .userId(userId)
                .type("deposit")
                .targetId(0L)
                .amount(amount)
                .status("pending")
                .referenceCode(referenceCode)
                .build();
        
        transactionRepository.save(transaction);
        
        User user = userRepository.findById(userId).orElse(null);
        notificationService.sendToAllAdmins(
                "Yêu cầu nạp tiền mới",
                "Người dùng " + (user != null ? user.getUsername() : userId) + " vừa gửi xác nhận đã chuyển khoản " + amount + "đ. Nội dung: " + referenceCode,
                "system",
                Map.of(
                    "transaction_id", transaction.getTransactionId(),
                    "amount", amount,
                    "reference_code", referenceCode,
                    "action", "approve_deposit"
                )
        );

        return ResponseEntity.ok(Map.of("success", true, "message", "Thông tin đã được ghi nhận, vui lòng chờ Admin duyệt!"));
    }

    @GetMapping("/history")
    public ResponseEntity<?> getTransactions() {
        Integer userId = getCurrentUserId();
        return ResponseEntity.ok(Map.of("success", true, "data", transactionRepository.findByUserIdOrderByCreatedAtDesc(userId)));
    }

    @GetMapping("/purchased-songs")
    public ResponseEntity<?> getPurchasedSongs() {
        Integer userId = getCurrentUserId();
        var purchased = purchasedSongRepository.findByUserUserId(userId);
        var songs = purchased.stream().map(ps -> ps.getSong()).collect(java.util.stream.Collectors.toList());
        songService.populateAccessInfo(songs, userId);
        return ResponseEntity.ok(Map.of("success", true, "data", songs));
    }

    @GetMapping("/check-purchase")
    public ResponseEntity<?> checkPurchase(@RequestParam Long targetId, @RequestParam String type) {
        Integer userId = getCurrentUserId();
        boolean purchased = false;
        Integer targetIdInt = targetId.intValue();
        if ("song".equals(type) || "purchase_song".equals(type)) {
            purchased = purchasedSongRepository.existsByUserUserIdAndSongSongId(userId, targetIdInt);
        } else if ("album".equals(type) || "purchase_album".equals(type)) {
            purchased = purchasedAlbumRepository.existsByUserUserIdAndAlbumAlbumId(userId, targetIdInt);
        }
        return ResponseEntity.ok(Map.of("success", true, "purchased", purchased));
    }

    @PostMapping("/premium/subscribe")
    @Transactional
    public ResponseEntity<?> subscribePremium(@RequestBody Map<String, Object> payload) {
        Integer userId = getCurrentUserId();
        User user = userRepository.findById(userId).orElseThrow();
        
        if (user.getIsPremium() != null && user.getIsPremium() == 1) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Bạn đã là thành viên Premium"));
        }

        Double price = 99000.0; // Hardcoded price for now
        if (user.getBalance() < price) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Số dư không đủ để đăng ký Premium"));
        }

        user.setBalance(user.getBalance() - price);
        user.setIsPremium(1);
        user.setPremiumExpiry(java.time.LocalDateTime.now().plusMonths(1));
        userRepository.save(user);

        transactionRepository.save(Transaction.builder()
                .userId(userId)
                .type("premium")
                .targetId(0L)
                .amount(price)
                .status("success")
                .build());

        notificationService.createNotification(user, 
                "Đọc ký Premium thành công", 
                "Chúc mừng! Bạn đã trở thành thành viên Premium. Hạn dùng đến: " + user.getPremiumExpiry(),
                "spend", 
                Map.of("expiry", user.getPremiumExpiry()));

        return ResponseEntity.ok(Map.of("success", true, "message", "Đăng ký thành viên Premium thành công"));
    }

    @GetMapping("/premium/cancel-preview")
    public ResponseEntity<?> cancelPremiumPreview() {
        Integer userId = getCurrentUserId();
        User user = userRepository.findById(userId).orElseThrow();

        if (user.getIsPremium() == null || user.getIsPremium() == 0 || user.getPremiumExpiry() == null 
                || user.getPremiumExpiry().isBefore(java.time.LocalDateTime.now())) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Bạn chưa đăng ký gói Premium hoặc gói đã hết hạn"));
        }

        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        java.time.LocalDateTime startDate = user.getPremiumExpiry().minusMonths(1);
        
        long secondsElapsed = java.time.Duration.between(startDate, now).getSeconds();
        double daysElapsed = (double) secondsElapsed / (24 * 60 * 60);

        double refundAmount = 0.0;
        double refundRatio = 0.0;

        if (daysElapsed >= 0 && daysElapsed <= 7.0) {
            refundAmount = 49500.0; // Hoàn 1 nửa số tiền
            refundRatio = 0.5;
        }

        long secondsRemaining = java.time.Duration.between(now, user.getPremiumExpiry()).getSeconds();
        if (secondsRemaining < 0) secondsRemaining = 0;
        long daysRemaining = (secondsRemaining + 86399) / 86400; // Làm tròn lên số ngày còn lại

        return ResponseEntity.ok(Map.of(
            "success", true,
            "data", Map.of(
                "days_remaining", daysRemaining,
                "days_elapsed", Math.round(daysElapsed * 10.0) / 10.0, // làm tròn 1 chữ số thập phân
                "refund_ratio", refundRatio,
                "refund_amount", refundAmount,
                "expiry_date", user.getPremiumExpiry().toString()
            )
        ));
    }

    @PostMapping("/premium/cancel")
    @Transactional
    public ResponseEntity<?> cancelPremium() {
        Integer userId = getCurrentUserId();
        User user = userRepository.findById(userId).orElseThrow();

        if (user.getIsPremium() == null || user.getIsPremium() == 0 || user.getPremiumExpiry() == null 
                || user.getPremiumExpiry().isBefore(java.time.LocalDateTime.now())) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Bạn chưa đăng ký gói Premium hoặc gói đã hết hạn"));
        }

        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        java.time.LocalDateTime startDate = user.getPremiumExpiry().minusMonths(1);
        
        long secondsElapsed = java.time.Duration.between(startDate, now).getSeconds();
        double daysElapsed = (double) secondsElapsed / (24 * 60 * 60);

        double refundAmount = 0.0;

        if (daysElapsed >= 0 && daysElapsed <= 7.0) {
            refundAmount = 49500.0;
        }

        long secondsRemaining = java.time.Duration.between(now, user.getPremiumExpiry()).getSeconds();
        if (secondsRemaining < 0) secondsRemaining = 0;
        long daysRemaining = (secondsRemaining + 86399) / 86400;

        // Cập nhật ví và trạng thái Premium của user
        user.setBalance(user.getBalance() + refundAmount);
        user.setIsPremium(0);
        user.setPremiumExpiry(null);
        userRepository.save(user);

        // Lưu Transaction hoàn tiền (luôn lưu vết dù 0đ hay >0đ)
        String description = refundAmount > 0 
                ? "Hủy gói Premium sớm trong vòng 7 ngày, hoàn lại 50% số tiền" 
                : "Hủy gói Premium sớm sau 7 ngày sử dụng, không hoàn tiền";
                
        transactionRepository.save(Transaction.builder()
                .userId(userId)
                .type("refund")
                .targetId(0L)
                .amount(refundAmount)
                .status("success")
                .description(description)
                .build());

        // Gửi thông báo
        String notificationMsg = refundAmount > 0 
                ? "Bạn đã hủy gói Premium thành công. Số tiền được hoàn lại: " + refundAmount + "đ vào ví."
                : "Bạn đã hủy gói Premium thành công. Không có tiền hoàn trả do đã quá 7 ngày sử dụng.";

        notificationService.createNotification(user, 
                "Hủy gói Premium thành công", 
                notificationMsg,
                "deposit", 
                Map.of("refundAmount", refundAmount));

        return ResponseEntity.ok(Map.of("success", true, "message", "Hủy gói Premium thành công", "refund_amount", refundAmount));
    }


    @PostMapping("/purchase/song")
    @Transactional
    public ResponseEntity<?> purchaseSong(@RequestBody Map<String, Long> payload) {
        Integer userId = getCurrentUserId();
        Long songIdLong = payload.get("songId");
        Integer songId = songIdLong.intValue();

        User user = userRepository.findById(userId).orElseThrow();
        var song = songRepository.findById(songId).orElseThrow();

        if (purchasedSongRepository.existsByUserUserIdAndSongSongId(userId, songId)) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Bạn đã mua bài hát này rồi"));
        }

        if (user.getBalance() < song.getPrice()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Số dư không đủ"));
        }

        user.setBalance(user.getBalance() - song.getPrice());
        userRepository.save(user);

        transactionRepository.save(Transaction.builder()
                .userId(userId)
                .targetId(songIdLong)
                .type("purchase_song")
                .amount(song.getPrice())
                .status("success")
                .build());

        PurchasedSong ps = new PurchasedSong();
        ps.setUser(user);
        ps.setSong(song);
        ps.setPricePaid(song.getPrice());
        ps.setPurchaseDate(java.time.LocalDateTime.now());
        purchasedSongRepository.save(ps);

        notificationService.createNotification(user, 
                "Mua nhạc thành công", 
                "Bạn đã mua bài hát \"" + song.getTitle() + "\" thành công.",
                "spend", 
                Map.of("songId", songId));

        // Notify Artist
        if (song.getArtistId() != null) {
            artistRepository.findById(song.getArtistId()).ifPresent(artist -> {
                if (artist.getUserId() != null) {
                    userRepository.findById(artist.getUserId()).ifPresent(artistUser -> {
                        notificationService.createNotification(artistUser,
                                "Bạn vừa nhận lời từ bài hát",
                                "Người dùng đã mua bài hát \"" + song.getTitle() + "\".",
                                "revenue",
                                Map.of("songId", songId, "amount", song.getPrice()));
                    });
                }
            });
        }

        return ResponseEntity.ok(Map.of("success", true, "message", "Mua bài hát thành công"));
    }

    @PostMapping("/purchase/album")
    @Transactional
    public ResponseEntity<?> purchaseAlbum(@RequestBody Map<String, Long> payload) {
        Integer userId = getCurrentUserId();
        Long albumIdLong = payload.get("albumId");
        Integer albumId = albumIdLong.intValue();

        User user = userRepository.findById(userId).orElseThrow();
        var album = albumRepository.findById(albumId).orElseThrow();

        if (purchasedAlbumRepository.existsByUserUserIdAndAlbumAlbumId(userId, albumId)) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Bạn đã mua album này rồi"));
        }

        if (user.getBalance() < album.getPrice()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Số dư không đủ"));
        }

        user.setBalance(user.getBalance() - album.getPrice());
        userRepository.save(user);

        transactionRepository.save(Transaction.builder()
                .userId(userId)
                .targetId(albumIdLong)
                .type("purchase_album")
                .amount(album.getPrice())
                .status("success")
                .build());

        PurchasedAlbum pa = new PurchasedAlbum();
        pa.setUser(user);
        pa.setAlbum(album);
        purchasedAlbumRepository.save(pa);

        notificationService.createNotification(user, 
                "Mua album thành công", 
                "Bạn đã mua album \"" + album.getTitle() + "\" thành công.",
                "spend", 
                Map.of("albumId", albumId));

        // Notify Artist
        if (album.getArtistId() != null) {
            artistRepository.findById(album.getArtistId()).ifPresent(artist -> {
                if (artist.getUserId() != null) {
                    userRepository.findById(artist.getUserId()).ifPresent(artistUser -> {
                        notificationService.createNotification(artistUser,
                                "Bạn vừa nhận lời từ album",
                                "Người dùng đã mua album \"" + album.getTitle() + "\".",
                                "revenue",
                                Map.of("albumId", albumId, "amount", album.getPrice()));
                    });
                }
            });
        }

        return ResponseEntity.ok(Map.of("success", true, "message", "Mua album thành công"));
    }

    @GetMapping("/purchased-albums")
    public ResponseEntity<?> getPurchasedAlbums() {
        Integer userId = getCurrentUserId();
        var purchased = purchasedAlbumRepository.findByUserUserId(userId);
        var albums = purchased.stream().map(pa -> pa.getAlbum()).collect(java.util.stream.Collectors.toList());
        return ResponseEntity.ok(Map.of("success", true, "data", albums));
    }

    @GetMapping("/stats")
    public ResponseEntity<?> getWalletStats() {
        Integer userId = getCurrentUserId();
        var transactions = transactionRepository.findByUserId(userId);
        
        double totalDeposited = transactions.stream()
                .filter(t -> "deposit".equals(t.getType()) && 
                        ("completed".equals(t.getStatus()) || "success".equals(t.getStatus()) || "approved".equals(t.getStatus())))
                .mapToDouble(Transaction::getAmount)
                .sum();
                
        double totalSpent = transactions.stream()
                .filter(t -> ("purchase_song".equals(t.getType()) || "purchase_album".equals(t.getType())) && 
                        ("completed".equals(t.getStatus()) || "success".equals(t.getStatus()) || "approved".equals(t.getStatus())))
                .mapToDouble(Transaction::getAmount)
                .sum();

        double totalSub = transactions.stream()
                .filter(t -> ("premium".equals(t.getType()) || "membership".equals(t.getType())) && 
                        ("completed".equals(t.getStatus()) || "success".equals(t.getStatus()) || "approved".equals(t.getStatus())))
                .mapToDouble(Transaction::getAmount)
                .sum();
                
        double totalRefund = transactions.stream()
                .filter(t -> "refund".equals(t.getType()) && 
                        ("completed".equals(t.getStatus()) || "success".equals(t.getStatus()) || "approved".equals(t.getStatus())))
                .mapToDouble(Transaction::getAmount)
                .sum();
                
        double netSub = Math.max(0.0, totalSub - totalRefund);
                
        return ResponseEntity.ok(Map.of("success", true, "data", Map.of(
                "total_deposited", totalDeposited,
                "total_spent", totalSpent,
                "total_subscription", netSub
        )));
    }
}
