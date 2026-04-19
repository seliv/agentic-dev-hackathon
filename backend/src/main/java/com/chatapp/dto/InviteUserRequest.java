package com.chatapp.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class InviteUserRequest {

    @NotNull(message = "User ID is required")
    private Long userId;

}
