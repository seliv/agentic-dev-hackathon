package com.chatapp.service;

import com.chatapp.entity.Friendship;
import com.chatapp.entity.User;
import com.chatapp.entity.UserBlock;
import com.chatapp.repository.FriendshipRepository;
import com.chatapp.repository.UserBlockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class BlockService {

    private final UserBlockRepository userBlockRepository;
    private final FriendshipRepository friendshipRepository;
    private final UserService userService;

    @Transactional
    public UserBlock blockUser(Long blockerId, Long blockedId) {
        if (blockerId.equals(blockedId)) {
            throw new IllegalArgumentException("Cannot block yourself");
        }

        if (userBlockRepository.existsByBlockerIdAndBlockedId(blockerId, blockedId)) {
            throw new IllegalStateException("User is already blocked");
        }

        // Remove any existing friendship
        Optional<Friendship> friendship = friendshipRepository.findBetweenUsers(blockerId, blockedId);
        friendship.ifPresent(friendshipRepository::delete);

        User blocker = userService.findById(blockerId);
        User blocked = userService.findById(blockedId);

        UserBlock block = new UserBlock();
        block.setBlocker(blocker);
        block.setBlocked(blocked);
        return userBlockRepository.save(block);
    }

    @Transactional
    public void unblockUser(Long blockerId, Long blockedId) {
        UserBlock block = userBlockRepository.findByBlockerIdAndBlockedId(blockerId, blockedId)
                .orElseThrow(() -> new IllegalArgumentException("User is not blocked"));
        userBlockRepository.delete(block);
    }

    public List<UserBlock> getBlockedUsers(Long blockerId) {
        return userBlockRepository.findByBlockerId(blockerId);
    }

    public boolean isBlocked(Long userIdA, Long userIdB) {
        return userBlockRepository.isBlockedEitherDirection(userIdA, userIdB);
    }

}
