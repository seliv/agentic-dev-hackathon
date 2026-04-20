package com.chatapp.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PresenceResponse {

    private Long userId;
    private String username;
    private String status;

}
