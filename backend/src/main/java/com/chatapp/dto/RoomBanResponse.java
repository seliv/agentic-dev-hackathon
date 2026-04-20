package com.chatapp.dto;

import com.chatapp.entity.RoomBan;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
public class RoomBanResponse {

    private Long id;
    private UUID roomId;
    private Long userId;
    private String username;
    private String displayName;
    private Long bannedById;
    private String bannedByUsername;
    private String reason;
    private Instant createdAt;

    public static RoomBanResponse fromEntity(RoomBan ban) {
        RoomBanResponse response = new RoomBanResponse();
        response.setId(ban.getId());
        response.setRoomId(ban.getRoom().getId());
        response.setUserId(ban.getUser().getId());
        response.setUsername(ban.getUser().getUsername());
        response.setDisplayName(ban.getUser().getDisplayName());
        response.setBannedById(ban.getBannedBy().getId());
        response.setBannedByUsername(ban.getBannedBy().getUsername());
        response.setReason(ban.getReason());
        response.setCreatedAt(ban.getCreatedAt());
        return response;
    }

}
