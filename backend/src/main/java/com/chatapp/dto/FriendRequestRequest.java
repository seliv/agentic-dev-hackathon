package com.chatapp.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class FriendRequestRequest {

    @NotNull(message = "User ID is required")
    private Long userId;

}
