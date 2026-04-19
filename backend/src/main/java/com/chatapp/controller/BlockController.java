package com.chatapp.controller;

import com.chatapp.dto.UserBlockResponse;
import com.chatapp.entity.UserBlock;
import com.chatapp.service.BlockService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class BlockController {

    private final BlockService blockService;

    @PostMapping("/api/users/{userId}/block")
    public ResponseEntity<UserBlockResponse> blockUser(
            @PathVariable Long userId,
            Authentication authentication) {
        Long blockerId = (Long) authentication.getPrincipal();
        UserBlock block = blockService.blockUser(blockerId, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(UserBlockResponse.fromEntity(block));
    }

    @DeleteMapping("/api/users/{userId}/block")
    public ResponseEntity<Void> unblockUser(
            @PathVariable Long userId,
            Authentication authentication) {
        Long blockerId = (Long) authentication.getPrincipal();
        blockService.unblockUser(blockerId, userId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/api/users/me/blocks")
    public ResponseEntity<List<UserBlockResponse>> getBlockedUsers(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        List<UserBlockResponse> blocks = blockService.getBlockedUsers(userId).stream()
                .map(UserBlockResponse::fromEntity)
                .toList();
        return ResponseEntity.ok(blocks);
    }

}
