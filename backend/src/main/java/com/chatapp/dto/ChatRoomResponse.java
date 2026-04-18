package com.chatapp.dto;

import com.chatapp.entity.ChatRoom;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
public class ChatRoomResponse {

    private UUID id;
    private String name;
    private String description;
    private String type;
    private Long ownerId;
    private String ownerUsername;
    private long memberCount;
    private Instant createdAt;

    public static ChatRoomResponse fromEntity(ChatRoom room, long memberCount) {
        ChatRoomResponse response = new ChatRoomResponse();
        response.setId(room.getId());
        response.setName(room.getName());
        response.setDescription(room.getDescription());
        response.setType(room.getType().name());
        response.setOwnerId(room.getOwner().getId());
        response.setOwnerUsername(room.getOwner().getUsername());
        response.setMemberCount(memberCount);
        response.setCreatedAt(room.getCreatedAt());
        return response;
    }

}
