package com.chatapp.controller;

import com.chatapp.dto.ChangePasswordRequest;
import com.chatapp.dto.DeleteAccountRequest;
import com.chatapp.dto.SignUpRequest;
import com.chatapp.dto.UserResponse;
import com.chatapp.dto.UserSearchResponse;
import com.chatapp.entity.User;
import com.chatapp.repository.UserRepository;
import com.chatapp.security.SessionConstants;
import com.chatapp.service.UserService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final UserRepository userRepository;
    private final FindByIndexNameSessionRepository<? extends Session> sessionRepository;

    @PostMapping("/signup")
    public ResponseEntity<UserResponse> signUp(
            @Valid @RequestBody SignUpRequest request,
            HttpSession session) {
        User user = userService.signUp(request);
        session.setAttribute(SessionConstants.SESSION_USER_KEY, user.getId());
        session.setAttribute(
                FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME,
                user.getEmail()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.fromEntity(user));
    }

    @PutMapping("/me/password")
    public ResponseEntity<Void> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        userService.changePassword(userId, request);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteAccount(
            @Valid @RequestBody DeleteAccountRequest request,
            Authentication authentication,
            HttpSession session) {
        Long userId = (Long) authentication.getPrincipal();
        User user = userService.findById(userId);

        userService.deleteAccount(userId, request.getPassword());

        Map<String, ? extends Session> sessions =
                sessionRepository.findByPrincipalName(user.getEmail());
        sessions.keySet().forEach(sessionRepository::deleteById);

        return ResponseEntity.ok().build();
    }

    @GetMapping("/search")
    public ResponseEntity<List<UserSearchResponse>> searchUsers(
            @RequestParam String q,
            Authentication authentication) {
        if (q == null || q.isBlank() || q.length() < 2) {
            return ResponseEntity.ok(List.of());
        }
        Long currentUserId = (Long) authentication.getPrincipal();
        List<UserSearchResponse> results = userRepository.searchUsers(q.trim(), PageRequest.of(0, 20)).stream()
                .filter(u -> !u.getId().equals(currentUserId))
                .map(UserSearchResponse::fromEntity)
                .toList();
        return ResponseEntity.ok(results);
    }

}
