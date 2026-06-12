package com.appad.controllers;

import com.appad.models.User;
import com.appad.services.AuthService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;
    private final com.appad.utils.JwtUtils jwtUtils;

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, Object> payload) {
        try {
            User user = new User();
            user.setUsername((String) payload.get("username"));
            user.setEmail((String) payload.get("email"));
            user.setPassword((String) payload.get("password"));
            user.setFullName((String) payload.get("full_name"));
            
            boolean isArtist = (boolean) payload.getOrDefault("artist_register", false);
            
            User registeredUser = authService.register(user, isArtist);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "User registered successfully");
            
            Map<String, Object> data = new HashMap<>();
            data.put("user_id", registeredUser.getUserId());
            data.put("username", registeredUser.getUsername());
            data.put("email", registeredUser.getEmail());
            data.put("role", registeredUser.getRole());
            data.put("token", jwtUtils.generateToken(registeredUser.getUserId(), registeredUser.getEmail()));
            
            response.put("data", data);
            return ResponseEntity.status(201).body(response);
        } catch (Exception e) {
            return ResponseEntity.status(400).body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest loginRequest) {
        try {
            return authService.authenticate(loginRequest.getEmail(), loginRequest.getPassword())
                    .map(user -> {
                        Map<String, Object> response = new HashMap<>();
                        response.put("success", true);
                        response.put("message", "Login successful");
                        
                        Map<String, Object> data = new HashMap<>();
                        data.put("user_id", user.getUserId());
                        data.put("username", user.getUsername());
                        data.put("email", user.getEmail());
                        data.put("role", user.getRole());
                        data.put("is_premium", user.getIsPremium());
                        data.put("full_name", user.getFullName());
                        data.put("avatar_url", user.getAvatarUrl());
                        data.put("balance", user.getBalance());
                        data.put("token", jwtUtils.generateToken(user.getUserId(), user.getEmail()));
                        
                        response.put("data", data);
                        return ResponseEntity.ok(response);
                    })
                    .orElse(ResponseEntity.status(401).body(Map.of("success", false, "message", "Invalid credentials")));
        } catch (Exception e) {
            return ResponseEntity.status(403).body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(@RequestHeader("Authorization") String token) {
        // Mock logic: assuming token is valid and extracting user_id
        // In reality, should decode JWT here
        return ResponseEntity.ok(Map.of("success", true, "data", Map.of("email", "test@test.com", "username", "testuser", "role", "user")));
    }

    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(@RequestBody Map<String, Object> payload) {
        try {
            Integer userId = Double.valueOf(payload.get("userId").toString()).intValue();
            String currentPassword = payload.get("currentPassword").toString();
            String newPassword = payload.get("newPassword").toString();

            boolean success = authService.changePassword(userId, currentPassword, newPassword);
            if (success) {
                return ResponseEntity.ok(Map.of("success", true, "message", "Đổi mật khẩu thành công"));
            } else {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Mật khẩu hiện tại không đúng"));
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @Data
    public static class LoginRequest {
        private String email;
        private String password;
    }
}
