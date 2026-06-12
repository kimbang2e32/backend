package com.appad.controllers;

import com.appad.models.Report;
import com.appad.repository.ReportRepository;
import com.appad.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {
    private final ReportRepository reportRepository;
    private final UserRepository userRepository;

    @PostMapping("/submit")
    public ResponseEntity<?> submitReport(@RequestBody Map<String, Object> payload) {
        Integer userId = Double.valueOf(payload.get("userId").toString()).intValue();
        Long targetId = Long.valueOf(payload.get("targetId").toString());
        String targetType = payload.get("targetType").toString();
        String reason = payload.get("reason").toString();
        String description = payload.get("description") != null ? payload.get("description").toString() : "";

        Report report = new Report();
        report.setUser(userRepository.findById(userId).orElseThrow());
        report.setTargetId(targetId);
        report.setTargetType(targetType);
        report.setReason(reason);
        report.setDescription(description);

        reportRepository.save(report);
        return ResponseEntity.ok(Map.of("success", true, "message", "Cảm ơn bạn đã báo cáo. Chúng tôi sẽ xử lý sớm nhất."));
    }

    @GetMapping("/all")
    public ResponseEntity<?> getAllReports() {
        return ResponseEntity.ok(reportRepository.findAll());
    }

    @PutMapping("/{id}/update-status")
    public ResponseEntity<?> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> payload) {
        String status = payload.get("status");
        return reportRepository.findById(id).map(report -> {
            report.setStatus(status);
            reportRepository.save(report);
            return ResponseEntity.ok(Map.of("success", true, "message", "Trạng thái báo cáo đã được cập nhật"));
        }).orElse(ResponseEntity.notFound().build());
    }
}
