package com.chatapp.dto;

import lombok.Data;
import java.time.Instant;

@Data
public class SessionResponse {

    private String sessionId;
    private Instant createdAt;
    private Instant lastAccessedAt;
    private boolean current;

}
