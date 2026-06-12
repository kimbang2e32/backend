package com.appad.controllers;

import com.appad.services.HistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/history")
@RequiredArgsConstructor
public class HistoryController {
    private final HistoryService historyService;

    @PostMapping("/record")
    public ResponseEntity<?> recordListen(@RequestBody Map<String, Object> payload) {
        Integer userId = ((Number) payload.get("userId")).intValue();
        Integer songId = ((Number) payload.get("songId")).intValue();
        Integer duration = payload.containsKey("durationSeconds") ? ((Number) payload.get("durationSeconds")).intValue() : null;
        Boolean isCompleted = payload.containsKey("isCompleted") ? (Boolean) payload.get("isCompleted") : null;
        boolean incrementCount = !payload.containsKey("incrementCount") || Boolean.TRUE.equals(payload.get("incrementCount"));

        historyService.recordListen(userId, songId, duration, isCompleted, incrementCount);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @GetMapping("/by-day")
    public ResponseEntity<?> getHistoryByDay(@RequestParam Integer userId, 
                                            @RequestParam(defaultValue = "100") int limit,
                                            @RequestParam(defaultValue = "0") int offset) {
        var data = historyService.getHistoryByDay(userId, limit, offset);
        return ResponseEntity.ok(Map.of("success", true, "data", data));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<?> getUserHistory(@PathVariable Integer userId) {
        // Fallback or legacy support
        return getHistoryByDay(userId, 100, 0);
    }
}
