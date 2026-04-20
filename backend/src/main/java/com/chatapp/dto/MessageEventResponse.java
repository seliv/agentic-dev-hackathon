package com.chatapp.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class MessageEventResponse {

    private String type;
    private Object data;

}
