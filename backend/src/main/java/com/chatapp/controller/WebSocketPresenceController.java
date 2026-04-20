package com.chatapp.controller;

import com.chatapp.dto.PresenceStatusRequest;
import com.chatapp.security.SessionConstants;
import com.chatapp.service.PresenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class WebSocketPresenceController {

    private final PresenceService presenceService;

    @MessageMapping("/presence/heartbeat")
    public void heartbeat(SimpMessageHeaderAccessor headerAccessor) {
        Long userId = (Long) headerAccessor.getSessionAttributes().get(SessionConstants.SESSION_USER_KEY);
        if (userId != null) {
            presenceService.heartbeat(userId);
        }
    }

    @MessageMapping("/presence/status")
    public void updateStatus(@Payload PresenceStatusRequest request, SimpMessageHeaderAccessor headerAccessor) {
        Long userId = (Long) headerAccessor.getSessionAttributes().get(SessionConstants.SESSION_USER_KEY);
        if (userId == null) return;

        if ("AFK".equals(request.getStatus())) {
            presenceService.setConnectionAFK(userId);
        } else if ("ACTIVE".equals(request.getStatus())) {
            presenceService.setConnectionActive(userId);
        }
    }

}
