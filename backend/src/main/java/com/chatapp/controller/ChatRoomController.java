package com.chatapp.controller;

import com.chatapp.dto.ChatRoomMemberResponse;
import com.chatapp.dto.ChatRoomResponse;
import com.chatapp.dto.CreateRoomRequest;
import com.chatapp.entity.ChatRoom;
import com.chatapp.entity.User;
import com.chatapp.service.ChatRoomService;
import com.chatapp.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class ChatRoomController {

    private final ChatRoomService chatRoomService;
    private final UserService userService;

    @PostMapping
    public ResponseEntity<ChatRoomResponse> createRoom(
            @Valid @RequestBody CreateRoomRequest request,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        User user = userService.findById(userId);
        ChatRoom room = chatRoomService.createRoom(request, user);
        long memberCount = chatRoomService.getMemberCount(room.getId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ChatRoomResponse.fromEntity(room, memberCount));
    }

    @GetMapping
    public ResponseEntity<List<ChatRoomResponse>> getMyRooms(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        List<ChatRoomResponse> rooms = chatRoomService.getUserRooms(userId).stream()
                .map(room -> ChatRoomResponse.fromEntity(room, chatRoomService.getMemberCount(room.getId())))
                .toList();
        return ResponseEntity.ok(rooms);
    }

    @GetMapping("/public")
    public ResponseEntity<Page<ChatRoomResponse>> getPublicRooms(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<ChatRoomResponse> rooms = chatRoomService.getPublicRooms(search, page, size)
                .map(room -> ChatRoomResponse.fromEntity(room, chatRoomService.getMemberCount(room.getId())));
        return ResponseEntity.ok(rooms);
    }

    @GetMapping("/{roomId}")
    public ResponseEntity<ChatRoomResponse> getRoom(
            @PathVariable UUID roomId,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        chatRoomService.validateMembership(roomId, userId);
        ChatRoom room = chatRoomService.findById(roomId);
        long memberCount = chatRoomService.getMemberCount(roomId);
        return ResponseEntity.ok(ChatRoomResponse.fromEntity(room, memberCount));
    }

    @PostMapping("/{roomId}/join")
    public ResponseEntity<Void> joinRoom(
            @PathVariable UUID roomId,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        User user = userService.findById(userId);
        chatRoomService.joinRoom(roomId, user);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{roomId}/leave")
    public ResponseEntity<Void> leaveRoom(
            @PathVariable UUID roomId,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        chatRoomService.leaveRoom(roomId, userId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{roomId}/members")
    public ResponseEntity<List<ChatRoomMemberResponse>> getMembers(
            @PathVariable UUID roomId,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        chatRoomService.validateMembership(roomId, userId);
        List<ChatRoomMemberResponse> members = chatRoomService.getMembers(roomId).stream()
                .map(ChatRoomMemberResponse::fromEntity)
                .toList();
        return ResponseEntity.ok(members);
    }

}
