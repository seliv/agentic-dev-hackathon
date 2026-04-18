package com.chatapp.controller;

import com.chatapp.dto.SessionResponse;
import com.chatapp.entity.User;
import com.chatapp.service.UserService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users/me/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final FindByIndexNameSessionRepository<? extends Session> sessionRepository;
    private final UserService userService;

    @GetMapping
    public ResponseEntity<List<SessionResponse>> listSessions(
            Authentication authentication,
            HttpSession currentSession) {
        Long userId = (Long) authentication.getPrincipal();
        User user = userService.findById(userId);

        Map<String, ? extends Session> sessions =
                sessionRepository.findByPrincipalName(user.getEmail());

        List<SessionResponse> responses = sessions.entrySet().stream()
                .map(entry -> {
                    Session session = entry.getValue();
                    SessionResponse response = new SessionResponse();
                    response.setSessionId(session.getId());
                    response.setCreatedAt(session.getCreationTime());
                    response.setLastAccessedAt(session.getLastAccessedTime());
                    response.setCurrent(session.getId().equals(currentSession.getId()));
                    return response;
                })
                .toList();

        return ResponseEntity.ok(responses);
    }

    @PostMapping("/{sessionId}/invalidate")
    public ResponseEntity<Void> invalidateSession(
            @PathVariable String sessionId,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        User user = userService.findById(userId);

        // Verify the session belongs to this user
        Map<String, ? extends Session> sessions =
                sessionRepository.findByPrincipalName(user.getEmail());

        if (!sessions.containsKey(sessionId)) {
            return ResponseEntity.notFound().build();
        }

        sessionRepository.deleteById(sessionId);
        return ResponseEntity.ok().build();
    }

}
