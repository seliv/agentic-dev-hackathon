package com.chatapp.dto;

import com.chatapp.entity.User;
import lombok.Data;

@Data
public class UserSearchResponse {

    private Long id;
    private String username;
    private String displayName;

    public static UserSearchResponse fromEntity(User user) {
        UserSearchResponse response = new UserSearchResponse();
        response.setId(user.getId());
        response.setUsername(user.getUsername());
        response.setDisplayName(user.getDisplayName());
        return response;
    }

}
