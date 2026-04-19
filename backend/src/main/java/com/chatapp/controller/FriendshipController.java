package com.chatapp.controller;

import com.chatapp.dto.FriendRequestRequest;
import com.chatapp.dto.FriendshipResponse;
import com.chatapp.entity.Friendship;
import com.chatapp.service.FriendshipService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/friends")
@RequiredArgsConstructor
public class FriendshipController {

    private final FriendshipService friendshipService;

    @PostMapping("/request")
    public ResponseEntity<FriendshipResponse> sendRequest(
            @Valid @RequestBody FriendRequestRequest request,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        Friendship friendship = friendshipService.sendRequest(userId, request.getUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(FriendshipResponse.fromEntity(friendship, userId));
    }

    @GetMapping
    public ResponseEntity<List<FriendshipResponse>> getFriends(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        List<FriendshipResponse> friends = friendshipService.getFriends(userId).stream()
                .map(f -> FriendshipResponse.fromEntity(f, userId))
                .toList();
        return ResponseEntity.ok(friends);
    }

    @GetMapping("/requests")
    public ResponseEntity<List<FriendshipResponse>> getPendingRequests(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        List<FriendshipResponse> requests = friendshipService.getPendingRequests(userId).stream()
                .map(f -> FriendshipResponse.fromEntity(f, userId))
                .toList();
        return ResponseEntity.ok(requests);
    }

    @PostMapping("/{id}/accept")
    public ResponseEntity<FriendshipResponse> acceptRequest(
            @PathVariable Long id,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        Friendship friendship = friendshipService.acceptRequest(id, userId);
        return ResponseEntity.ok(FriendshipResponse.fromEntity(friendship, userId));
    }

    @PostMapping("/{id}/decline")
    public ResponseEntity<FriendshipResponse> declineRequest(
            @PathVariable Long id,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        Friendship friendship = friendshipService.declineRequest(id, userId);
        return ResponseEntity.ok(FriendshipResponse.fromEntity(friendship, userId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> removeFriend(
            @PathVariable Long id,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        friendshipService.removeFriend(id, userId);
        return ResponseEntity.ok().build();
    }

}
