package com.chatapp.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class MarkAsReadRequest {

    @NotNull(message = "lastReadMessageId is required")
    private UUID lastReadMessageId;

}
