package com.appad.config;

import com.appad.utils.JwtUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Component
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {
    private final JwtUtils jwtUtils;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        
        String path = request.getRequestURI();
        System.out.println("JwtFilter: Checking request for path: " + path);
        
        String authHeader = request.getHeader("Authorization");
        
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            System.out.println("JwtFilter: Token found, validating...");
            if (jwtUtils.validateToken(token)) {
                Integer userId = jwtUtils.getUserIdFromToken(token);
                String email = jwtUtils.getEmailFromToken(token);
                System.out.println("JwtFilter: Token valid for User ID: " + userId + ", Email: " + email);
                
                // Set authentication in security context
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        userId, // Using userId as principal
                        null,
                        Collections.emptyList()
                );
                
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } else {
                System.out.println("JwtFilter: Token validation FAILED");
            }
        } else {
            System.out.println("JwtFilter: No Bearer token found in Authorization header");
        }
        
        filterChain.doFilter(request, response);
    }
}
