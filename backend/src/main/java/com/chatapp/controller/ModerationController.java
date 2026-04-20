package com.chatapp.controller;

import com.chatapp.dto.BanUserRequest;
import com.chatapp.dto.ChangeRoleRequest;
import com.chatapp.dto.ChatRoomMemberResponse;
import com.chatapp.dto.RoomBanResponse;
import com.chatapp.entity.ChatRoomMember;
import com.chatapp.entity.MemberRole;
import com.chatapp.entity.RoomBan;
import com.chatapp.service.ModerationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/rooms/{roomId}")
@RequiredArgsConstructor
public class ModerationController {

    private final ModerationService moderationService;

    @PutMapping("/members/{userId}/role")
    public ResponseEntity<ChatRoomMemberResponse> changeRole(
            @PathVariable UUID roomId,
            @PathVariable Long userId,
            @Valid @RequestBody ChangeRoleRequest request,
            Authentication authentication) {
        Long callerId = (Long) authentication.getPrincipal();
        MemberRole newRole = MemberRole.valueOf(request.getRole().toUpperCase());
        ChatRoomMember updated = moderationService.changeRole(roomId, userId, newRole, callerId);
        return ResponseEntity.ok(ChatRoomMemberResponse.fromEntity(updated));
    }

    @PostMapping("/bans")
    public ResponseEntity<RoomBanResponse> banUser(
            @PathVariable UUID roomId,
            @Valid @RequestBody BanUserRequest request,
            Authentication authentication) {
        Long callerId = (Long) authentication.getPrincipal();
        RoomBan ban = moderationService.banUser(roomId, request.getUserId(), callerId, request.getReason());
        return ResponseEntity.status(HttpStatus.CREATED).body(RoomBanResponse.fromEntity(ban));
    }

    @DeleteMapping("/bans/{userId}")
    public ResponseEntity<Void> unbanUser(
            @PathVariable UUID roomId,
            @PathVariable Long userId,
            Authentication authentication) {
        Long callerId = (Long) authentication.getPrincipal();
        moderationService.unbanUser(roomId, userId, callerId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/bans")
    public ResponseEntity<List<RoomBanResponse>> getBannedUsers(
            @PathVariable UUID roomId,
            Authentication authentication) {
        Long callerId = (Long) authentication.getPrincipal();
        List<RoomBanResponse> bans = moderationService.getBannedUsers(roomId, callerId).stream()
                .map(RoomBanResponse::fromEntity)
                .toList();
        return ResponseEntity.ok(bans);
    }

}
