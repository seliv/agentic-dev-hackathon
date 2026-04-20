package com.chatapp.controller;

import com.chatapp.dto.MarkAsReadRequest;
import com.chatapp.dto.UnreadCountResponse;
import com.chatapp.service.ChatRoomService;
import com.chatapp.service.UnreadService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class UnreadController {

    private final UnreadService unreadService;
    private final ChatRoomService chatRoomService;

    @PostMapping("/{roomId}/read")
    public ResponseEntity<Void> markAsRead(
            @PathVariable UUID roomId,
            @Valid @RequestBody MarkAsReadRequest request,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        chatRoomService.validateMembership(roomId, userId);
        unreadService.markAsRead(userId, roomId, request.getLastReadMessageId());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/unread")
    public ResponseEntity<List<UnreadCountResponse>> getUnreadCounts(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        List<UnreadCountResponse> counts = unreadService.getUnreadCounts(userId).entrySet().stream()
                .map(e -> new UnreadCountResponse(e.getKey(), e.getValue()))
                .toList();
        return ResponseEntity.ok(counts);
    }

}
