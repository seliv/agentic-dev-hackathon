package com.chatapp.controller;

import com.chatapp.dto.InviteUserRequest;
import com.chatapp.dto.RoomInvitationResponse;
import com.chatapp.entity.RoomInvitation;
import com.chatapp.service.RoomInvitationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class RoomInvitationController {

    private final RoomInvitationService invitationService;

    @PostMapping("/api/rooms/{roomId}/invitations")
    public ResponseEntity<RoomInvitationResponse> inviteUser(
            @PathVariable UUID roomId,
            @Valid @RequestBody InviteUserRequest request,
            Authentication authentication) {
        Long inviterId = (Long) authentication.getPrincipal();
        RoomInvitation invitation = invitationService.inviteUser(roomId, inviterId, request.getUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(RoomInvitationResponse.fromEntity(invitation));
    }

    @GetMapping("/api/users/me/invitations")
    public ResponseEntity<List<RoomInvitationResponse>> getPendingInvitations(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        List<RoomInvitationResponse> invitations = invitationService.getPendingInvitations(userId).stream()
                .map(RoomInvitationResponse::fromEntity)
                .toList();
        return ResponseEntity.ok(invitations);
    }

    @PostMapping("/api/invitations/{id}/accept")
    public ResponseEntity<RoomInvitationResponse> acceptInvitation(
            @PathVariable Long id,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        RoomInvitation invitation = invitationService.acceptInvitation(id, userId);
        return ResponseEntity.ok(RoomInvitationResponse.fromEntity(invitation));
    }

    @PostMapping("/api/invitations/{id}/decline")
    public ResponseEntity<RoomInvitationResponse> declineInvitation(
            @PathVariable Long id,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        RoomInvitation invitation = invitationService.declineInvitation(id, userId);
        return ResponseEntity.ok(RoomInvitationResponse.fromEntity(invitation));
    }

}
