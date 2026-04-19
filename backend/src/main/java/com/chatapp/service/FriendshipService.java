package com.chatapp.service;

import com.chatapp.entity.Friendship;
import com.chatapp.entity.FriendshipStatus;
import com.chatapp.entity.User;
import com.chatapp.repository.FriendshipRepository;
import com.chatapp.repository.UserBlockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class FriendshipService {

    private final FriendshipRepository friendshipRepository;
    private final UserBlockRepository userBlockRepository;
    private final UserService userService;
    private final SimpMessagingTemplate messagingTemplate;

    @Transactional
    public Friendship sendRequest(Long requesterId, Long addresseeId) {
        if (requesterId.equals(addresseeId)) {
            throw new IllegalArgumentException("Cannot send friend request to yourself");
        }

        if (userBlockRepository.isBlockedEitherDirection(requesterId, addresseeId)) {
            throw new IllegalStateException("Cannot send friend request to this user");
        }

        friendshipRepository.findBetweenUsers(requesterId, addresseeId).ifPresent(existing -> {
            throw new IllegalStateException("A friend request already exists between these users");
        });

        User requester = userService.findById(requesterId);
        User addressee = userService.findById(addresseeId);

        Friendship friendship = new Friendship();
        friendship.setRequester(requester);
        friendship.setAddressee(addressee);
        friendship.setStatus(FriendshipStatus.PENDING);
        friendship = friendshipRepository.save(friendship);

        messagingTemplate.convertAndSendToUser(
                addresseeId.toString(),
                "/queue/notifications",
                Map.of("type", "FRIEND_REQUEST", "data", Map.of(
                        "friendshipId", friendship.getId(),
                        "fromUserId", requesterId,
                        "fromUsername", requester.getUsername(),
                        "fromDisplayName", requester.getDisplayName()
                ))
        );

        return friendship;
    }

    @Transactional
    public Friendship acceptRequest(Long friendshipId, Long userId) {
        Friendship friendship = friendshipRepository.findById(friendshipId)
                .orElseThrow(() -> new IllegalArgumentException("Friend request not found"));

        if (!friendship.getAddressee().getId().equals(userId)) {
            throw new IllegalStateException("Only the addressee can accept this request");
        }

        if (friendship.getStatus() != FriendshipStatus.PENDING) {
            throw new IllegalStateException("Friend request is not pending");
        }

        friendship.setStatus(FriendshipStatus.ACCEPTED);
        return friendshipRepository.save(friendship);
    }

    @Transactional
    public Friendship declineRequest(Long friendshipId, Long userId) {
        Friendship friendship = friendshipRepository.findById(friendshipId)
                .orElseThrow(() -> new IllegalArgumentException("Friend request not found"));

        if (!friendship.getAddressee().getId().equals(userId)) {
            throw new IllegalStateException("Only the addressee can decline this request");
        }

        if (friendship.getStatus() != FriendshipStatus.PENDING) {
            throw new IllegalStateException("Friend request is not pending");
        }

        friendship.setStatus(FriendshipStatus.DECLINED);
        return friendshipRepository.save(friendship);
    }

    @Transactional
    public void removeFriend(Long friendshipId, Long userId) {
        Friendship friendship = friendshipRepository.findById(friendshipId)
                .orElseThrow(() -> new IllegalArgumentException("Friendship not found"));

        if (!friendship.getRequester().getId().equals(userId) &&
            !friendship.getAddressee().getId().equals(userId)) {
            throw new IllegalStateException("You are not part of this friendship");
        }

        if (friendship.getStatus() != FriendshipStatus.ACCEPTED) {
            throw new IllegalStateException("Friendship is not active");
        }

        friendshipRepository.delete(friendship);
    }

    public List<Friendship> getFriends(Long userId) {
        return friendshipRepository.findByUserIdAndStatus(userId, FriendshipStatus.ACCEPTED);
    }

    public List<Friendship> getPendingRequests(Long userId) {
        return friendshipRepository.findPendingByUserId(userId);
    }

    public boolean areFriends(Long userIdA, Long userIdB) {
        return friendshipRepository.areFriends(userIdA, userIdB);
    }

}
