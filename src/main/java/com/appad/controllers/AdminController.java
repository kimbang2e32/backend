package com.appad.controllers;

import com.appad.models.Transaction;
import com.appad.models.User;
import com.appad.repository.TransactionRepository;
import com.appad.repository.UserRepository;
import com.appad.repository.ArtistRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final ArtistRepository artistRepository;
    private final com.appad.repository.SongRepository songRepository;
    private final com.appad.repository.AlbumRepository albumRepository;
    private final com.appad.repository.ListeningHistoryRepository historyRepository;
    private final com.appad.repository.ArtistMembershipRepository artistMembershipRepository;
    private final com.appad.services.NotificationService notificationService;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

    // Fix for Jackson if needed, or simply use Map
    private class ObjectMapper extends com.fasterxml.jackson.databind.ObjectMapper {
        public ObjectMapper() {
            registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
            disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        }
    }

    // --- USER MANAGEMENT ---

    @GetMapping("/users")
    public ResponseEntity<?> getAllUsers() {
        return ResponseEntity.ok(userRepository.findAll());
    }

    @PutMapping("/users/{id}/ban")
    public ResponseEntity<?> banUser(@PathVariable Integer id) {
        return userRepository.findById(id).map(user -> {
            user.setRole("BANNED"); // Simple implementation
            userRepository.save(user);
            return ResponseEntity.ok(Map.of("success", true, "message", "User banned"));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/users/{id}/unban")
    public ResponseEntity<?> unbanUser(@PathVariable Integer id) {
        return userRepository.findById(id).map(user -> {
            user.setRole("USER"); 
            userRepository.save(user);
            return ResponseEntity.ok(Map.of("success", true, "message", "User unbanned"));
        }).orElse(ResponseEntity.notFound().build());
    }

    // --- TRANSACTION MANAGEMENT (DEPOSITS) ---

    @GetMapping("/transactions/pending")
    public ResponseEntity<?> getPendingDeposits() {
        return ResponseEntity.ok(transactionRepository.findByTypeAndStatus("deposit", "pending"));
    }

    @PostMapping("/transactions/{id}/approve")
    @Transactional
    public ResponseEntity<?> approveDeposit(@PathVariable Integer id) {
        Transaction transaction = transactionRepository.findById(id).orElseThrow();
        if (!"pending".equals(transaction.getStatus())) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Transaction is not pending"));
        }

        User user = userRepository.findById(transaction.getUserId()).orElseThrow();
        user.setBalance(user.getBalance() + transaction.getAmount());
        userRepository.save(user);

        transaction.setStatus("completed");
        transactionRepository.save(transaction);

        notificationService.createNotification(user, 
                "Nạp tiền thành công", 
                "Yêu cầu nạp " + transaction.getAmount() + "đ của bạn đã được duyệt. Số dư mới: " + user.getBalance() + "đ.",
                "deposit_approved", 
                Map.of("transaction_id", id, "newBalance", user.getBalance()));

        return ResponseEntity.ok(Map.of("success", true, "message", "Deposit approved"));
    }

    @PostMapping("/transactions/{id}/reject")
    @Transactional
    public ResponseEntity<?> rejectDeposit(@PathVariable Integer id) {
        Transaction transaction = transactionRepository.findById(id).orElseThrow();
        transaction.setStatus("cancelled");
        transactionRepository.save(transaction);

        userRepository.findById(transaction.getUserId()).ifPresent(user -> {
            notificationService.createNotification(user, 
                    "Nạp tiền bị từ chối", 
                    "Yêu cầu nạp " + transaction.getAmount() + "đ của bạn đã bị từ chối.",
                    "deposit_rejected", 
                    Map.of("transaction_id", id));
        });

        return ResponseEntity.ok(Map.of("success", true, "message", "Deposit rejected"));
    }

    // --- WITHDRAWAL MANAGEMENT (now using transactions table with type='withdrawal') ---

    @GetMapping("/withdrawals/pending")
    public ResponseEntity<?> getPendingWithdrawals() {
        return ResponseEntity.ok(transactionRepository.findByTypeAndStatus("withdrawal", "pending"));
    }

    @PostMapping("/withdrawals/{id}/approve")
    @Transactional
    public ResponseEntity<?> approveWithdrawal(@PathVariable Integer id) {
        Transaction withdrawal = transactionRepository.findById(id).orElseThrow();
        if (!"pending".equals(withdrawal.getStatus()) || !"withdrawal".equals(withdrawal.getType())) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Withdrawal is not pending"));
        }

        withdrawal.setStatus("approved");
        transactionRepository.save(withdrawal);

        // Notify artist
        if (withdrawal.getTargetId() != null) {
            artistRepository.findById(withdrawal.getTargetId().intValue()).ifPresent(artist -> {
                if (artist.getUserId() != null) {
                    userRepository.findById(artist.getUserId()).ifPresent(artistUser -> {
                        notificationService.createNotification(artistUser, 
                                "Yêu cầu rút tiền thành công", 
                                "Yêu cầu rút " + withdrawal.getAmount() + "đ của bạn đã được duyệt.",
                                "withdrawal_approved", 
                                Map.of("withdrawal_id", id));
                    });
                }
            });
        }

        return ResponseEntity.ok(Map.of("success", true, "message", "Withdrawal approved"));
    }

    @PostMapping("/withdrawals/{id}/reject")
    @Transactional
    public ResponseEntity<?> rejectWithdrawal(@PathVariable Integer id) {
        Transaction withdrawal = transactionRepository.findById(id).orElseThrow();
        if (!"pending".equals(withdrawal.getStatus()) || !"withdrawal".equals(withdrawal.getType())) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Withdrawal is not pending"));
        }

        // Hoàn tiền lại cho artist vì khi yêu cầu đã tạm trừ
        if (withdrawal.getTargetId() != null) {
            artistRepository.findById(withdrawal.getTargetId().intValue()).ifPresent(artist -> {
                artist.setWalletBalance(artist.getWalletBalance() + withdrawal.getAmount());
                artistRepository.save(artist);
                
                // Notify artist
                if (artist.getUserId() != null) {
                    userRepository.findById(artist.getUserId()).ifPresent(artistUser -> {
                        notificationService.createNotification(artistUser, 
                                "Yêu cầu rút tiền bị từ chối", 
                                "Yêu cầu rút " + withdrawal.getAmount() + "đ của bạn đã bị từ chối. Số tiền đã được hoàn lại ví nghệ sĩ.",
                                "withdrawal_rejected", 
                                Map.of("withdrawal_id", id));
                    });
                }
            });
        }

        withdrawal.setStatus("rejected");
        transactionRepository.save(withdrawal);

        return ResponseEntity.ok(Map.of("success", true, "message", "Withdrawal rejected and funds returned"));
    }

    @GetMapping("/stats")
    public ResponseEntity<?> getAdminStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalUsers", userRepository.count());
        stats.put("totalArtists", artistRepository.count());
        stats.put("totalSongs", songRepository.count());
        stats.put("totalAlbums", albumRepository.count());
        // Count only premium streams for total plays
        long premiumPlays = historyRepository.findAll().stream()
                .filter(h -> Boolean.TRUE.equals(h.getIsPremiumStream()))
                .count();
        stats.put("totalPlays", premiumPlays);
        
        // New users this month
        LocalDateTime firstDayOfMonth = LocalDateTime.now().withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);
        stats.put("newUsersThisMonth", userRepository.findAll().stream()
                .filter(u -> u.getCreatedAt() != null && u.getCreatedAt().isAfter(firstDayOfMonth))
                .count());

        stats.put("pendingWithdrawals", transactionRepository.findByTypeAndStatus("withdrawal", "pending").size());
        stats.put("pendingDeposits", transactionRepository.findByTypeAndStatus("deposit", "pending").size());
        
        return ResponseEntity.ok(Map.of("success", true, "data", stats));
    }

    // --- ARTIST APPROVAL ---

    @GetMapping("/artists/pending")
    public ResponseEntity<?> getPendingArtists() {
        // isBanned = 2 means pending artist
        return ResponseEntity.ok(userRepository.findByIsBanned(2));
    }

    @PostMapping("/artists/{id}/approve")
    @Transactional
    public ResponseEntity<?> approveArtist(@PathVariable Integer id) {
        User user = userRepository.findById(id).orElseThrow();
        user.setRole("artist");
        user.setIsBanned(0);
        userRepository.save(user);

        // Create Artist profile if not exists
        artistRepository.findByUserId(id).ifPresentOrElse(
            artist -> {},
            () -> {
                com.appad.models.Artist artist = new com.appad.models.Artist();
                artist.setUserId(id);
                artist.setName(user.getFullName() != null ? user.getFullName() : user.getUsername());
                artist.setBio("Cửa hàng âm nhạc của " + artist.getName());
                artist.setWalletBalance(0.0);
                artistRepository.save(artist);
            }
        );

        notificationService.createNotification(user, 
                "Chúc mừng! Bạn đã là Nghệ sĩ", 
                "Yêu cầu trở thành nghệ sĩ của bạn đã được duyệt. Hãy bắt đầu sáng tạo!", 
                "system", null);

        return ResponseEntity.ok(Map.of("success", true, "message", "Artist approved"));
    }

    @PostMapping("/artists/{id}/reject")
    @Transactional
    public ResponseEntity<?> rejectArtist(@PathVariable Integer id) {
        User user = userRepository.findById(id).orElseThrow();
        user.setIsBanned(0);
        userRepository.save(user);

        notificationService.createNotification(user, 
                "Yêu cầu bị từ chối", 
                "Yêu cầu trở thành nghệ sĩ của bạn không được chấp nhận vào lúc này.", 
                "system", null);

        return ResponseEntity.ok(Map.of("success", true, "message", "Artist request rejected"));
    }

    // --- MUSIC MODERATION ---

    @GetMapping("/songs")
    public ResponseEntity<?> getAllSongsAdmin() {
        return ResponseEntity.ok(songRepository.findAll());
    }

    @DeleteMapping("/songs/{id}")
    @Transactional
    public ResponseEntity<?> deleteSongAdmin(@PathVariable Integer id) {
        songRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("success", true, "message", "Song deleted by admin"));
    }

    @DeleteMapping("/albums/{id}")
    @Transactional
    public ResponseEntity<?> deleteAlbumAdmin(@PathVariable Integer id) {
        albumRepository.deleteById(id);
        // Also need to delete or detach songs? 
        // In simple JPA often cascade delete handles it or child songs just stay orphan.
        return ResponseEntity.ok(Map.of("success", true, "message", "Album deleted by admin"));
    }

    // --- BROADCAST ---

    @PostMapping("/broadcast")
    public ResponseEntity<?> broadcastNotification(@RequestBody Map<String, String> payload) {
        String title = payload.get("title");
        String message = payload.get("message");
        notificationService.broadcast(title, message, "system", null);
        return ResponseEntity.ok(Map.of("success", true, "message", "Broadcast sent to all users"));
    }

    // --- MISSING ENDPOINTS IMPLEMENTATION ---

    // 1. Albums
    @GetMapping("/albums")
    public ResponseEntity<?> getAllAlbumsAdmin(@RequestParam(required = false) Integer limit) {
        // Returns List<Map> as expected by AdminAlbumsActivity
        List<com.appad.models.Album> albums = albumRepository.findAll();
        // Enrich with artist name if needed, but Album model usually has it or use simpler DTO
        // For now return raw albums, or map to include artistName
        List<Map<String, Object>> result = albums.stream().map(album -> {
            Map<String, Object> map = new HashMap<>();
            map.put("album_id", album.getAlbumId());
            map.put("title", album.getTitle());
            map.put("artist_id", album.getArtistId());
            map.put("cover_url", album.getCoverUrl());
            map.put("release_date", album.getReleaseDate());
            map.put("price", album.getPrice());
            map.put("is_premium", album.getIsPremium());
            
            // Fetch artist name
            artistRepository.findById(album.getArtistId()).ifPresent(artist -> {
                map.put("artistName", artist.getName());
                map.put("artist_name", artist.getName());
            });
            
            // Count songs
            map.put("songCount", songRepository.countByAlbumId(album.getAlbumId()));
            map.put("song_count", map.get("songCount"));
            
            return map;
        }).toList();
        return ResponseEntity.ok(result);
    }

    @GetMapping("/albums/all")
    public ResponseEntity<?> getAllAlbumsSimpleAdmin() {
        // Returns Map { data: [...] } for AdminEditSongActivity selectors
        List<com.appad.models.Album> albums = albumRepository.findAll();
        return ResponseEntity.ok(Map.of("success", true, "data", albums));
    }

    // 2. Transactions (Wallet Deposits)
    @GetMapping("/transactions")
    public ResponseEntity<?> getAllTransactions(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer limit) {
        
        List<Transaction> transactions = transactionRepository.findAll();
        // Filter by 'type' column (deposit, purchase, subscription, etc.)
        if (type != null && !type.isEmpty()) {
            transactions = transactions.stream().filter(t -> t.getType() != null && t.getType().equalsIgnoreCase(type)).toList();
        }
        if (status != null && !status.isEmpty()) {
            // Status values: pending, completed, cancelled
            transactions = transactions.stream().filter(t -> t.getStatus().equalsIgnoreCase(status)).toList();
        }
        
        // Build response with snake_case keys
        List<Map<String, Object>> enriched = transactions.stream().map(t -> {
            Map<String, Object> map = new HashMap<>();
            map.put("transaction_id", t.getTransactionId());
            map.put("user_id", t.getUserId());
            map.put("type", t.getType());
            map.put("amount", t.getAmount());
            map.put("status", t.getStatus());
            map.put("description", t.getDescription());
            map.put("reference_code", t.getReferenceCode());
            map.put("target_id", t.getTargetId());
            map.put("created_at", t.getCreatedAt() != null ? t.getCreatedAt().toString() : null);
            
            // Enrich with user info
            userRepository.findById(t.getUserId()).ifPresent(u -> {
                map.put("user_name", u.getFullName() != null ? u.getFullName() : u.getUsername());
                map.put("username", u.getUsername());
                map.put("full_name", u.getFullName());
                map.put("email", u.getEmail());
            });
            return map;
        }).toList();

        return ResponseEntity.ok(Map.of("success", true, "data", enriched));
    }

    // 3. Withdrawals (from transactions table with type='withdrawal')
    @GetMapping("/withdrawals")
    public ResponseEntity<?> getAllWithdrawals(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer limit) {
        
        // Get all transactions with type='withdrawal'
        List<Transaction> withdrawals = transactionRepository.findAll().stream()
                .filter(t -> "withdrawal".equalsIgnoreCase(t.getType()))
                .toList();
        
        if (status != null && !status.isEmpty()) {
            withdrawals = withdrawals.stream().filter(w -> status.equalsIgnoreCase(w.getStatus())).toList();
        }
        
        List<Map<String, Object>> enriched = withdrawals.stream().map(w -> {
            Map<String, Object> map = new HashMap<>();
            map.put("withdrawal_id", w.getTransactionId());
            map.put("artist_id", w.getTargetId().intValue());
            map.put("amount", w.getAmount());
            map.put("status", w.getStatus());
            map.put("bank_name", w.getBankName());
            map.put("account_number", w.getAccountNumber());
            map.put("account_holder_name", w.getAccountHolderName());
            map.put("note", w.getDescription());
            map.put("created_at", w.getCreatedAt() != null ? w.getCreatedAt().toString() : null);
            map.put("updated_at", w.getUpdatedAt() != null ? w.getUpdatedAt().toString() : null);
            
            // Enrich with artist name
            artistRepository.findById(w.getTargetId().intValue()).ifPresent(a -> {
                map.put("artist_name", a.getName());
            });
            return map;
        }).toList();
        
        return ResponseEntity.ok(Map.of("success", true, "data", enriched));
    }

    // 4. Memberships
    @GetMapping("/memberships")
    public ResponseEntity<?> getAllMemberships(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer limit) {
             
         List<com.appad.models.ArtistMembership> memberships = artistMembershipRepository.findAll();
         // Filter
         if (status != null && !status.isEmpty()) {
             memberships = memberships.stream().filter(m -> m.getStatus().equals(status)).toList(); 
         }
         
         List<Map<String, Object>> enriched = memberships.stream().map(m -> {
             Map<String, Object> map = new HashMap<>();
             map.put("membership_id", m.getMembershipId());
             map.put("user_id", m.getUserId());
             map.put("artist_id", m.getArtistId());
             map.put("price_paid", m.getPricePaid());
             map.put("start_date", m.getStartDate() != null ? m.getStartDate().toString() : null);
             map.put("expiry_date", m.getExpiryDate() != null ? m.getExpiryDate().toString() : null);
             map.put("status", m.getStatus());
             map.put("created_at", m.getCreatedAt() != null ? m.getCreatedAt().toString() : null);
             
             // Enrich with user info
             userRepository.findById(m.getUserId()).ifPresent(u -> {
                 map.put("username", u.getUsername());
                 map.put("full_name", u.getFullName());
             });
             // Enrich with artist info
             artistRepository.findById(m.getArtistId()).ifPresent(a -> {
                 map.put("artist_name", a.getName());
             });
             return map;
         }).toList();
         
         return ResponseEntity.ok(Map.of("success", true, "data", enriched));
    }

    @GetMapping("/memberships/stats")
    public ResponseEntity<?> getMembershipStats() {
        List<com.appad.models.ArtistMembership> all = artistMembershipRepository.findAll();
        
        long activeCount = all.stream().filter(m -> "active".equalsIgnoreCase(m.getStatus())).count();
        double totalRevenue = all.stream().mapToDouble(m -> m.getPricePaid() != null ? m.getPricePaid() : 0.0).sum();
        
        Map<String, Object> stats = new HashMap<>();
        stats.put("active_count", activeCount);
        stats.put("total_revenue", totalRevenue);
        
        return ResponseEntity.ok(Map.of("success", true, "data", stats));
    }
}
