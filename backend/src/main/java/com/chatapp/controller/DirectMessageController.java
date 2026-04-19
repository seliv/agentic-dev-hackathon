package com.chatapp.controller;

import com.chatapp.dto.ChatRoomResponse;
import com.chatapp.entity.ChatRoom;
import com.chatapp.entity.User;
import com.chatapp.service.BlockService;
import com.chatapp.service.ChatRoomService;
import com.chatapp.service.FriendshipService;
import com.chatapp.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/direct-messages")
@RequiredArgsConstructor
public class DirectMessageController {

    private final ChatRoomService chatRoomService;
    private final FriendshipService friendshipService;
    private final BlockService blockService;
    private final UserService userService;

    @PostMapping("/{userId}")
    public ResponseEntity<ChatRoomResponse> getOrCreateDMRoom(
            @PathVariable Long userId,
            Authentication authentication) {
        Long currentUserId = (Long) authentication.getPrincipal();

        if (currentUserId.equals(userId)) {
            throw new IllegalArgumentException("Cannot create a DM with yourself");
        }

        if (blockService.isBlocked(currentUserId, userId)) {
            throw new IllegalStateException("Cannot create a DM with this user");
        }

        if (!friendshipService.areFriends(currentUserId, userId)) {
            throw new IllegalStateException("You must be friends to start a direct message");
        }

        User currentUser = userService.findById(currentUserId);
        User otherUser = userService.findById(userId);
        ChatRoom room = chatRoomService.findOrCreateDirectRoom(currentUser, otherUser);
        long memberCount = chatRoomService.getMemberCount(room.getId());
        return ResponseEntity.ok(ChatRoomResponse.fromEntity(room, memberCount));
    }

}
