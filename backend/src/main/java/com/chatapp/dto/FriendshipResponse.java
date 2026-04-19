package com.chatapp.dto;

import com.chatapp.entity.Friendship;
import com.chatapp.entity.User;
import lombok.Data;

import java.time.Instant;

@Data
public class FriendshipResponse {

    private Long id;
    private Long friendUserId;
    private String friendUsername;
    private String friendDisplayName;
    private String status;
    private String direction;
    private Instant createdAt;

    public static FriendshipResponse fromEntity(Friendship friendship, Long currentUserId) {
        FriendshipResponse response = new FriendshipResponse();
        response.setId(friendship.getId());
        response.setStatus(friendship.getStatus().name());
        response.setCreatedAt(friendship.getCreatedAt());

        boolean isRequester = friendship.getRequester().getId().equals(currentUserId);
        response.setDirection(isRequester ? "OUTGOING" : "INCOMING");

        User friend = isRequester ? friendship.getAddressee() : friendship.getRequester();
        response.setFriendUserId(friend.getId());
        response.setFriendUsername(friend.getUsername());
        response.setFriendDisplayName(friend.getDisplayName());

        return response;
    }

}
