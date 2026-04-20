package com.chatapp.config;

import com.chatapp.security.SessionConstants;
import com.chatapp.service.PresenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketEventListener {

    private final PresenceService presenceService;

    @EventListener
    public void handleSessionConnect(SessionConnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        Long userId = getUserId(accessor);
        if (userId != null) {
            presenceService.connect(userId);
            log.debug("WebSocket connected: userId={}", userId);
        }
    }

    @EventListener
    public void handleSessionDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        Long userId = getUserId(accessor);
        if (userId != null) {
            presenceService.disconnect(userId);
            log.debug("WebSocket disconnected: userId={}", userId);
        }
    }

    private Long getUserId(StompHeaderAccessor accessor) {
        Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
        if (sessionAttributes == null) return null;
        return (Long) sessionAttributes.get(SessionConstants.SESSION_USER_KEY);
    }

}
