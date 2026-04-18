package com.chatapp.dto;

import com.chatapp.entity.Message;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
public class MessageResponse {

    private UUID id;
    private UUID roomId;
    private Long senderId;
    private String senderUsername;
    private String senderDisplayName;
    private String content;
    private Instant createdAt;
    private Instant updatedAt;

    public static MessageResponse fromEntity(Message message) {
        MessageResponse response = new MessageResponse();
        response.setId(message.getId());
        response.setRoomId(message.getRoom().getId());
        response.setSenderId(message.getSender().getId());
        response.setSenderUsername(message.getSender().getUsername());
        response.setSenderDisplayName(message.getSender().getDisplayName());
        response.setContent(message.getContent());
        response.setCreatedAt(message.getCreatedAt());
        response.setUpdatedAt(message.getUpdatedAt());
        return response;
    }

}
