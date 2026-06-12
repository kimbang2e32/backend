package com.appad.services;

import com.appad.models.Artist;
import com.appad.models.RevenueSharing;
import com.appad.repository.ArtistRepository;
import com.appad.repository.PremiumListeningStatsRepository;
import com.appad.repository.RevenueSharingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RevenueService {
    private final PremiumListeningStatsRepository statsRepository;
    private final RevenueSharingRepository revenueRepository;
    private final ArtistRepository artistRepository;

    @Transactional
    public void calculateMonthlyRevenue(int year, int month) {
        LocalDate startDate = LocalDate.of(year, month, 1);
        LocalDate endDate = startDate.plusMonths(1).minusDays(1);

        List<Object[]> stats = statsRepository.findStreamStatsByPeriod(startDate, endDate);
        
        long totalStreamsInMonth = stats.stream().mapToLong(row -> (long) row[1]).sum();
        if (totalStreamsInMonth == 0) return;

        // Giả sử tổng quỹ chi trả cho Premium là 1,000,000,000đ (Placeholder)
        double totalPool = 100000000.0; 
        double pricePerStream = totalPool / totalStreamsInMonth;

        for (Object[] row : stats) {
            Long artistId = (Long) row[0];
            Long artistStreams = (Long) row[1];
            double shareAmount = artistStreams * pricePerStream;

            Artist artist = artistRepository.findById(artistId.intValue()).orElseThrow();
            
            // Lưu record chia doanh thu
            RevenueSharing revenue = new RevenueSharing();
            revenue.setArtistId(artist.getArtistId());
            revenue.setArtistShare(shareAmount);
            revenue.setArtistPercentage(70.0);
            revenue.setShareType("premium_stream");
            revenue.setTotalAmount(totalPool);
            revenueRepository.save(revenue);

            // Cộng vào ví nghệ sĩ
            artist.setWalletBalance(artist.getWalletBalance() + shareAmount);
            artistRepository.save(artist);
            
            System.out.println("Đã chia " + shareAmount + "đ cho nghệ sĩ " + artist.getName());
        }
    }
}
