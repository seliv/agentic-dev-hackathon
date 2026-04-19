package com.chatapp.dto;

import com.chatapp.entity.UserBlock;
import lombok.Data;

import java.time.Instant;

@Data
public class UserBlockResponse {

    private Long id;
    private Long blockedUserId;
    private String blockedUsername;
    private Instant createdAt;

    public static UserBlockResponse fromEntity(UserBlock block) {
        UserBlockResponse response = new UserBlockResponse();
        response.setId(block.getId());
        response.setBlockedUserId(block.getBlocked().getId());
        response.setBlockedUsername(block.getBlocked().getUsername());
        response.setCreatedAt(block.getCreatedAt());
        return response;
    }

}
