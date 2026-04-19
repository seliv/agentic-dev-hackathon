package com.chatapp.dto;

import com.chatapp.entity.RoomInvitation;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
public class RoomInvitationResponse {

    private Long id;
    private UUID roomId;
    private String roomName;
    private Long inviterId;
    private String inviterUsername;
    private String status;
    private Instant createdAt;

    public static RoomInvitationResponse fromEntity(RoomInvitation invitation) {
        RoomInvitationResponse response = new RoomInvitationResponse();
        response.setId(invitation.getId());
        response.setRoomId(invitation.getRoom().getId());
        response.setRoomName(invitation.getRoom().getName());
        response.setInviterId(invitation.getInviter().getId());
        response.setInviterUsername(invitation.getInviter().getUsername());
        response.setStatus(invitation.getStatus().name());
        response.setCreatedAt(invitation.getCreatedAt());
        return response;
    }

}
