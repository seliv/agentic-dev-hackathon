package com.chatapp.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@Getter
@AllArgsConstructor
public class UnreadCountResponse {

    private UUID roomId;
    private long count;

}
