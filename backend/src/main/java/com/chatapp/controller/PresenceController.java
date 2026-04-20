package com.chatapp.controller;

import com.chatapp.dto.PresenceResponse;
import com.chatapp.service.ChatRoomService;
import com.chatapp.service.PresenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class PresenceController {

    private final ChatRoomService chatRoomService;
    private final PresenceService presenceService;

    @GetMapping("/{roomId}/members/presence")
    public ResponseEntity<List<PresenceResponse>> getRoomMemberPresence(
            @PathVariable UUID roomId,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        chatRoomService.validateMembership(roomId, userId);

        List<Long> memberIds = chatRoomService.getMembers(roomId).stream()
                .map(member -> member.getUser().getId())
                .toList();

        List<PresenceResponse> presences = presenceService.getPresenceForUsers(memberIds);
        return ResponseEntity.ok(presences);
    }

}
