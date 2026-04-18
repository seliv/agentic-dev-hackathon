package com.chatapp.exception;

public class NotRoomMemberException extends RuntimeException {
    public NotRoomMemberException(String message) {
        super(message);
    }
}
