package com.chatapp.controller;

import com.chatapp.dto.SignInRequest;
import com.chatapp.dto.UserResponse;
import com.chatapp.entity.User;
import com.chatapp.service.UserService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private static final String SESSION_USER_KEY = "user_id";

    @PostMapping("/signin")
    public ResponseEntity<UserResponse> signIn(
            @Valid @RequestBody SignInRequest request,
            HttpSession session) {
        User user = userService.signIn(request);
        session.setAttribute(SESSION_USER_KEY, user.getId());
        session.setAttribute(
                FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME,
                user.getEmail()
        );
        return ResponseEntity.ok(UserResponse.fromEntity(user));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpSession session) {
        session.invalidate();
        return ResponseEntity.ok().build();
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser(HttpSession session) {
        Long userId = (Long) session.getAttribute(SESSION_USER_KEY);
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }
        try {
            User user = userService.findById(userId);
            if (user.getDeletedAt() != null) {
                session.invalidate();
                return ResponseEntity.status(401).build();
            }
            return ResponseEntity.ok(UserResponse.fromEntity(user));
        } catch (Exception e) {
            session.invalidate();
            return ResponseEntity.status(401).build();
        }
    }

}
