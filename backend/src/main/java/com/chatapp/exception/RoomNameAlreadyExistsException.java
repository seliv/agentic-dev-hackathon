package com.chatapp.exception;

public class RoomNameAlreadyExistsException extends RuntimeException {
    public RoomNameAlreadyExistsException(String message) {
        super(message);
    }
}
