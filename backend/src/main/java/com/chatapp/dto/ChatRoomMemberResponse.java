package com.chatapp.dto;

import com.chatapp.entity.ChatRoomMember;
import lombok.Data;

import java.time.Instant;

@Data
public class ChatRoomMemberResponse {

    private Long userId;
    private String username;
    private String displayName;
    private String role;
    private Instant joinedAt;

    public static ChatRoomMemberResponse fromEntity(ChatRoomMember member) {
        ChatRoomMemberResponse response = new ChatRoomMemberResponse();
        response.setUserId(member.getUser().getId());
        response.setUsername(member.getUser().getUsername());
        response.setDisplayName(member.getUser().getDisplayName());
        response.setRole(member.getRole().name());
        response.setJoinedAt(member.getJoinedAt());
        return response;
    }

}
