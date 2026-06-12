package com.appad.services;

import com.appad.models.Artist;
import com.appad.repository.ArtistRepository;
import com.appad.repository.PurchasedSongRepository;
import com.appad.repository.SongRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ArtistService {
    private final ArtistRepository artistRepository;
    private final SongRepository songRepository;
    private final PurchasedSongRepository purchasedSongRepository;

    public List<Artist> getAllArtists(int limit, int offset) {
        // Simple findAll for now, logic matched later
        return artistRepository.findAll();
    }

    public Optional<Artist> getArtistById(Integer id) {
        return artistRepository.findById(id);
    }

    public Optional<Artist> getArtistByUserId(Integer userId) {
        return artistRepository.findByUserId(userId);
    }

    public Artist createArtist(Artist artist) {
        return artistRepository.save(artist);
    }

    public Artist updateArtist(Integer id, Artist artistData) {
        Artist artist = artistRepository.findById(id).orElseThrow(() -> new RuntimeException("Artist not found"));
        if (artistData.getName() != null) artist.setName(artistData.getName());
        if (artistData.getBio() != null) artist.setBio(artistData.getBio());
        if (artistData.getImageUrl() != null) artist.setImageUrl(artistData.getImageUrl());
        if (artistData.getCountry() != null) artist.setCountry(artistData.getCountry());
        if (artistData.getBankName() != null) artist.setBankName(artistData.getBankName());
        if (artistData.getBankAccount() != null) artist.setBankAccount(artistData.getBankAccount());
        if (artistData.getBankAccountName() != null) artist.setBankAccountName(artistData.getBankAccountName());
        
        return artistRepository.save(artist);
    }

    public java.util.Map<String, Object> getArtistDashboard(Integer artistId) {
        Artist artist = artistRepository.findById(artistId)
            .orElseThrow(() -> new RuntimeException("Artist not found"));
        
        java.util.Map<String, Object> dashboard = new java.util.HashMap<>();
        
        // Artist info
        java.util.Map<String, Object> artistInfo = new java.util.HashMap<>();
        artistInfo.put("artist_id", artist.getArtistId());
        artistInfo.put("name", artist.getName());
        artistInfo.put("image_url", artist.getImageUrl());
        dashboard.put("artist", artistInfo);
        
        // Wallet - using artist's wallet_balance field
        java.util.Map<String, Object> wallet = new java.util.HashMap<>();
        wallet.put("balance", artist.getWalletBalance() != null ? artist.getWalletBalance() : 0.0);
        dashboard.put("wallet", wallet);
        
        // Stats - count songs, purchases, listens from database
        java.util.Map<String, Object> stats = new java.util.HashMap<>();
        long totalSongs = songRepository.countByArtistId(artistId);
        long totalListens = songRepository.sumListenCountByArtistId(artistId);
        long totalPurchases = purchasedSongRepository.countByArtistId(artistId);
        
        stats.put("total_songs", totalSongs);
        stats.put("total_listens", totalListens);
        stats.put("total_purchases", totalPurchases);
        dashboard.put("stats", stats);
        
        // Unpaid amount (pending withdrawals or shares not yet paid)
        java.util.Map<String, Object> unpaid = new java.util.HashMap<>();
        unpaid.put("unpaid_amount", 0); // Will be calculated from revenue shares
        dashboard.put("unpaid", unpaid);
        
        // Revenue stats - aggregate by share type
        java.util.List<java.util.Map<String, Object>> revenueStats = new java.util.ArrayList<>();
        // Simplified: just return total purchases as "direct_purchase" type
        if (totalPurchases > 0) {
            java.util.Map<String, Object> directPurchase = new java.util.HashMap<>();
            directPurchase.put("share_type", "direct_purchase");
            directPurchase.put("count", totalPurchases);
            directPurchase.put("total_artist_share", artist.getWalletBalance() != null ? artist.getWalletBalance() : 0.0);
            revenueStats.add(directPurchase);
        }
        dashboard.put("revenue_stats", revenueStats);
        
        return dashboard;
    }

    public void deleteArtist(Integer id) {
        artistRepository.deleteById(id);
    }
    public void updateMembership(Integer id, java.util.Map<String, Object> payload) {
        Artist artist = artistRepository.findById(id).orElseThrow(() -> new RuntimeException("Artist not found"));
        if (payload.containsKey("membershipPrice")) {
            Object priceObj = payload.get("membershipPrice");
            if (priceObj instanceof Number) {
                artist.setMembershipPrice(((Number) priceObj).doubleValue());
            } else {
                 artist.setMembershipPrice(Double.valueOf(priceObj.toString()));
            }
        }
        if (payload.containsKey("membershipDurationDays")) {
            Object durationObj = payload.get("membershipDurationDays");
            if (durationObj instanceof Number) {
                artist.setMembershipDurationDays(((Number) durationObj).intValue());
            } else {
                artist.setMembershipDurationDays(Integer.valueOf(durationObj.toString()));
            }
        }
        artistRepository.save(artist);
    }
}
