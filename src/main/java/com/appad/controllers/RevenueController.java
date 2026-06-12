package com.appad.controllers;

import com.appad.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/revenue")
@RequiredArgsConstructor
public class RevenueController {

    private final ListeningHistoryRepository historyRepository;
    private final ArtistRepository artistRepository;
    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final RevenueSharingRepository sharingRepository;

    /**
     * Calculate premium revenue to distribute to artists based on listening duration
     * Premium pool = percentage of total premium revenue (e.g., 70%)
     */
    @PostMapping("/calculate-monthly")
    public ResponseEntity<?> calculateMonthlyPayout(@RequestBody(required = false) Map<String, Object> payload) {
        try {
            // Default: calculate from the last payout or last 30 days
            LocalDateTime endDate = LocalDateTime.now();
            
            // Find the most recent payout date in revenue_sharing
            LocalDateTime startDate = sharingRepository.findFirstByShareTypeOrderByCreatedAtDesc("premium_stream")
                    .map(com.appad.models.RevenueSharing::getCreatedAt)
                    .orElse(endDate.minusDays(30));
            
            if (payload != null) {
                if (payload.get("start_date") != null) {
                    startDate = LocalDateTime.parse(payload.get("start_date").toString());
                }
                if (payload.get("end_date") != null) {
                    endDate = LocalDateTime.parse(payload.get("end_date").toString());
                }
            }

            final LocalDateTime finalStartDate = startDate;
            final LocalDateTime finalEndDate = endDate;

            // Get all premium listening history in the period
            List<com.appad.models.ListeningHistory> premiumHistory = historyRepository.findAll().stream()
                    .filter(h -> h.getDay() != null 
                            && h.getDay().isAfter(finalStartDate) 
                            && h.getDay().isBefore(finalEndDate)
                            && Boolean.TRUE.equals(h.getIsPremiumStream()))
                    .collect(Collectors.toList());

            // Calculate total premium revenue (from premium subscriptions in period)
            // Status có thể là "completed", "success", hoặc "approved" tùy vào luồng xử lý
            double totalPremiumRevenue = transactionRepository.findAll().stream()
                    .filter(t -> "premium".equals(t.getType()) 
                            && ("completed".equals(t.getStatus()) || "success".equals(t.getStatus()) || "approved".equals(t.getStatus()))
                            && t.getCreatedAt() != null
                            && !t.getCreatedAt().isBefore(finalStartDate)  // >= startDate (inclusive)
                            && !t.getCreatedAt().isAfter(finalEndDate))    // <= endDate (inclusive)
                    .mapToDouble(t -> t.getAmount() != null ? t.getAmount() : 0)
                    .sum();

            // Artist pool = 70% of premium revenue
            double artistPool = totalPremiumRevenue * 0.7;
            
            // Calculate total duration by artist
            Map<Integer, Long> artistDurations = new HashMap<>();
            Map<Integer, Integer> artistStreams = new HashMap<>();
            
            for (var history : premiumHistory) {
                Integer artistId = history.getArtistId();
                if (artistId != null) {
                    long duration = history.getTotalDuration() != null ? history.getTotalDuration().longValue() : 0L;
                    artistDurations.merge(artistId, duration, Long::sum);
                    artistStreams.merge(artistId, 1, Integer::sum);
                }
            }

            long totalDuration = artistDurations.values().stream().mapToLong(Long::longValue).sum();

            // Calculate each artist's share
            List<Map<String, Object>> artistShares = new ArrayList<>();
            for (Map.Entry<Integer, Long> entry : artistDurations.entrySet()) {
                Integer artistId = entry.getKey();
                long duration = entry.getValue();
                
                double percentage = totalDuration > 0 ? (duration * 100.0 / totalDuration) : 0;
                double revenue = artistPool * (percentage / 100.0);
                
                Map<String, Object> share = new HashMap<>();
                share.put("artist_id", artistId);
                share.put("duration", duration);
                share.put("streams", artistStreams.getOrDefault(artistId, 0));
                share.put("percentage", Math.round(percentage * 100.0) / 100.0);
                share.put("revenue", Math.round(revenue));
                
                // Get artist name
                artistRepository.findById(artistId).ifPresent(artist -> {
                    share.put("artist_name", artist.getName());
                    share.put("image_url", artist.getImageUrl());
                });
                
                artistShares.add(share);
            }

            // Sort by revenue descending
            artistShares.sort((a, b) -> Double.compare(
                    ((Number) b.get("revenue")).doubleValue(),
                    ((Number) a.get("revenue")).doubleValue()
            ));

            Map<String, Object> result = new HashMap<>();
            result.put("start_date", startDate.toString());
            result.put("end_date", endDate.toString());
            result.put("total_premium_revenue", totalPremiumRevenue);
            result.put("total_pool", artistPool);
            result.put("total_duration", totalDuration);
            result.put("artist_shares", artistShares);

            return ResponseEntity.ok(Map.of("success", true, "data", result));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * Apply the calculated payout to artist wallets
     */
    @PostMapping("/apply-monthly")
    @Transactional
    public ResponseEntity<?> applyMonthlyPayout(@RequestBody Map<String, Object> payoutData) {
        try {
            List<Map<String, Object>> artistShares = (List<Map<String, Object>>) payoutData.get("artist_shares");
            if (artistShares == null || artistShares.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "No artist shares provided"));
            }

            LocalDateTime now = LocalDateTime.now();
            String batchTime = now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            double totalPool = payoutData.get("total_pool") != null 
                    ? ((Number) payoutData.get("total_pool")).doubleValue() : 0;

            int successCount = 0;
            for (Map<String, Object> share : artistShares) {
                Integer artistId = ((Number) share.get("artist_id")).intValue();
                double revenue = ((Number) share.get("revenue")).doubleValue();
                
                if (revenue > 0) {
                    // Get artist and their userId
                    var artistOpt = artistRepository.findById(artistId);
                    if (artistOpt.isEmpty()) continue;
                    
                    var artist = artistOpt.get();
                    Integer artistUserId = artist.getUserId();
                    
                    // Update artist wallet
                    double currentBalance = artist.getWalletBalance() != null ? artist.getWalletBalance() : 0;
                    artist.setWalletBalance(currentBalance + revenue);
                    double currentEarned = artist.getTotalEarned() != null ? artist.getTotalEarned() : 0;
                    artist.setTotalEarned(currentEarned + revenue);
                    artistRepository.save(artist);

                    // Save payout record to revenue_sharing only
                    com.appad.models.RevenueSharing sharing = new com.appad.models.RevenueSharing();
                    sharing.setArtistId(artistId);
                    sharing.setUserId(artistUserId);
                    sharing.setArtistShare(revenue);
                    sharing.setArtistPercentage(((Number) share.get("percentage")).doubleValue());
                    sharing.setShareType("premium_stream");
                    sharing.setTotalAmount(totalPool); 
                    sharing.setCreatedAt(now); 
                    
                    sharingRepository.save(sharing);
                    
                    successCount++;
                }
            }

            return ResponseEntity.ok(Map.of(
                    "success", true, 
                    "message", "Đã phát lương cho " + successCount + " nghệ sĩ",
                    "batch_time", batchTime
            ));
        } catch (Exception e) {
            System.err.println("=== PAYOUT ERROR ===");
            e.printStackTrace();
            String errorMsg = e.getMessage();
            if (e.getCause() != null) {
                errorMsg += " | Cause: " + e.getCause().getMessage();
            }
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", errorMsg));
        }
    }

    /**
     * Get payout history (batch summaries)
     */
    @GetMapping("/payout-history")
    public ResponseEntity<?> getPayoutHistory() {
        try {
            List<com.appad.models.RevenueSharing> allPayouts = sharingRepository.findByShareType("premium_stream");
            
            // Group by formatted created_at to identify batches
            DateTimeFormatter batchFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            Map<String, List<com.appad.models.RevenueSharing>> grouped = allPayouts.stream()
                    .filter(p -> p.getCreatedAt() != null)
                    .collect(Collectors.groupingBy(p -> p.getCreatedAt().format(batchFormatter)));

            List<Map<String, Object>> batches = new ArrayList<>();
            for (Map.Entry<String, List<com.appad.models.RevenueSharing>> entry : grouped.entrySet()) {
                String batchTime = entry.getKey();
                List<com.appad.models.RevenueSharing> payouts = entry.getValue();
                
                double totalPaid = payouts.stream()
                        .mapToDouble(p -> p.getArtistShare() != null ? p.getArtistShare() : 0.0)
                        .sum();
                
                Map<String, Object> batch = new HashMap<>();
                batch.put("batch_time", batchTime);
                batch.put("artist_count", payouts.size());
                batch.put("total_paid", totalPaid);
                
                batches.add(batch);
            }

            // Sort by batch_time descending
            batches.sort((a, b) -> ((String) b.get("batch_time")).compareTo((String) a.get("batch_time")));

            return ResponseEntity.ok(Map.of("success", true, "data", batches));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * Get details of a specific payout batch
     */
    @GetMapping("/payout-batch")
    public ResponseEntity<?> getPayoutBatchDetails(@RequestParam String batch_time) {
        try {
            DateTimeFormatter batchFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            List<com.appad.models.RevenueSharing> allPayouts = sharingRepository.findByShareType("premium_stream");
            
            List<com.appad.models.RevenueSharing> batchPayouts = allPayouts.stream()
                    .filter(p -> p.getCreatedAt() != null && p.getCreatedAt().format(batchFormatter).equals(batch_time))
                    .collect(Collectors.toList());
            
            List<Map<String, Object>> artists = new ArrayList<>();
            // Tính tổng y hệt cách tính ở ngoài (history summary)
            double totalPaid = batchPayouts.stream()
                    .mapToDouble(p -> p.getArtistShare() != null ? p.getArtistShare() : 0.0)
                    .sum();
            
            for (com.appad.models.RevenueSharing payout : batchPayouts) {
                Map<String, Object> artist = new HashMap<>();
                Integer artistId = payout.getArtistId();
                LocalDateTime currentCreatedAt = payout.getCreatedAt();
                
                // Dynamic duration calculation
                // 1. Find the previous payout for this artist
                com.appad.models.RevenueSharing prevPayout = sharingRepository
                        .findTopByArtistIdAndShareTypeAndCreatedAtBeforeOrderByCreatedAtDesc(artistId, "premium_stream", currentCreatedAt);
                
                LocalDateTime batchStart = (prevPayout != null) ? prevPayout.getCreatedAt() : LocalDateTime.of(2000, 1, 1, 0, 0);
                
                // 2. Sum duration from ListeningHistory in this interval
                List<com.appad.models.ListeningHistory> batchHistory = historyRepository.findPremiumStreamsInRange(artistId, batchStart, currentCreatedAt);
                long calculatedDuration = batchHistory.stream()
                        .mapToLong(h -> h.getTotalDuration() != null ? h.getTotalDuration().longValue() : 0L)
                        .sum();

                artist.put("artist_id", artistId);
                double artistShare = payout.getArtistShare() != null ? payout.getArtistShare() : 0;
                artist.put("revenue", artistShare);
                
                double percentage = totalPaid > 0 ? (artistShare * 100.0 / totalPaid) : 0;
                artist.put("percentage", Math.round(percentage * 100.0) / 100.0);
                
                artist.put("duration", calculatedDuration);
                artist.put("duration_text", (calculatedDuration / 60) + "p " + (calculatedDuration % 60) + "s");
                
                artistRepository.findById(payout.getArtistId()).ifPresent(a -> {
                    artist.put("artist_name", a.getName());
                    artist.put("image_url", a.getImageUrl());
                });
                
                artists.add(artist);
            }

            Map<String, Object> result = new HashMap<>();
            result.put("artists", artists);
            result.put("artist_count", artists.size());
            result.put("total_paid", totalPaid);
            result.put("batch_time", batch_time);
            result.put("period_start", batch_time); // To match mobile expectations

            return ResponseEntity.ok(Map.of("success", true, "data", result));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }
}
