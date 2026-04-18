package com.chatapp.dto;

import com.chatapp.entity.User;
import lombok.Data;
import java.time.Instant;

@Data
public class UserResponse {

    private Long id;
    private String email;
    private String displayName;
    private Instant createdAt;
    private Instant updatedAt;

    public static UserResponse fromEntity(User user) {
        UserResponse response = new UserResponse();
        response.setId(user.getId());
        response.setEmail(user.getEmail());
        response.setDisplayName(user.getDisplayName());
        response.setCreatedAt(user.getCreatedAt());
        response.setUpdatedAt(user.getUpdatedAt());
        return response;
    }

}
