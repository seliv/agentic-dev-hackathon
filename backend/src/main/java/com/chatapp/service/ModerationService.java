package com.chatapp.service;

import com.chatapp.entity.ChatRoom;
import com.chatapp.entity.ChatRoomMember;
import com.chatapp.entity.MemberRole;
import com.chatapp.entity.RoomBan;
import com.chatapp.entity.User;
import com.chatapp.exception.NotRoomMemberException;
import com.chatapp.repository.ChatRoomMemberRepository;
import com.chatapp.repository.RoomBanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ModerationService {

    private final ChatRoomMemberRepository memberRepository;
    private final RoomBanRepository banRepository;
    private final ChatRoomService chatRoomService;
    private final UserService userService;
    private final SimpMessagingTemplate messagingTemplate;

    @Transactional
    public RoomBan banUser(UUID roomId, Long targetUserId, Long callerUserId, String reason) {
        ChatRoom room = chatRoomService.findById(roomId);
        ChatRoomMember caller = getMemberOrThrow(roomId, callerUserId);
        validateModeratorRole(caller);

        if (targetUserId.equals(callerUserId)) {
            throw new IllegalArgumentException("Cannot ban yourself");
        }

        if (room.getOwner().getId().equals(targetUserId)) {
            throw new IllegalStateException("Cannot ban the room owner");
        }

        ChatRoomMember target = memberRepository.findByRoomIdAndUserId(roomId, targetUserId).orElse(null);
        if (target != null) {
            validateCallerOutranksTarget(caller, target);
        }

        if (banRepository.existsByRoomIdAndUserId(roomId, targetUserId)) {
            throw new IllegalStateException("User is already banned from this room");
        }

        if (target != null) {
            memberRepository.delete(target);
        }

        RoomBan ban = new RoomBan();
        ban.setRoom(room);
        ban.setUser(target != null ? target.getUser() : userService.findById(targetUserId));
        ban.setBannedBy(caller.getUser());
        ban.setReason(reason);
        ban = banRepository.save(ban);

        messagingTemplate.convertAndSendToUser(
                String.valueOf(targetUserId),
                "/queue/notifications",
                Map.of("type", "ROOM_BANNED", "data", Map.of("roomId", roomId, "roomName", room.getName()))
        );

        messagingTemplate.convertAndSend(
                "/topic/rooms/" + roomId + "/events",
                Map.of("type", "MEMBER_REMOVED", "data", Map.of("userId", targetUserId))
        );

        return ban;
    }

    @Transactional
    public void unbanUser(UUID roomId, Long targetUserId, Long callerUserId) {
        ChatRoomMember caller = getMemberOrThrow(roomId, callerUserId);
        validateModeratorRole(caller);

        if (!banRepository.existsByRoomIdAndUserId(roomId, targetUserId)) {
            throw new IllegalArgumentException("User is not banned from this room");
        }

        banRepository.deleteByRoomIdAndUserId(roomId, targetUserId);
    }

    public List<RoomBan> getBannedUsers(UUID roomId, Long callerUserId) {
        ChatRoomMember caller = getMemberOrThrow(roomId, callerUserId);
        validateModeratorRole(caller);
        return banRepository.findByRoomId(roomId);
    }

    @Transactional
    public ChatRoomMember changeRole(UUID roomId, Long targetUserId, MemberRole newRole, Long callerUserId) {
        ChatRoom room = chatRoomService.findById(roomId);

        if (!room.getOwner().getId().equals(callerUserId)) {
            throw new IllegalStateException("Only the room owner can change member roles");
        }

        if (targetUserId.equals(callerUserId)) {
            throw new IllegalArgumentException("Cannot change your own role");
        }

        if (newRole == MemberRole.OWNER) {
            throw new IllegalArgumentException("Cannot assign OWNER role");
        }

        ChatRoomMember target = getMemberOrThrow(roomId, targetUserId);
        if (target.getRole() == MemberRole.OWNER) {
            throw new IllegalStateException("Cannot change the owner's role");
        }

        target.setRole(newRole);
        target = memberRepository.save(target);

        messagingTemplate.convertAndSend(
                "/topic/rooms/" + roomId + "/events",
                Map.of("type", "MEMBER_ROLE_CHANGED", "data", Map.of(
                        "userId", targetUserId,
                        "username", target.getUser().getUsername(),
                        "newRole", newRole.name()
                ))
        );

        return target;
    }

    @Transactional
    public RoomBan kickMember(UUID roomId, Long targetUserId, Long callerUserId) {
        return banUser(roomId, targetUserId, callerUserId, null);
    }

    private ChatRoomMember getMemberOrThrow(UUID roomId, Long userId) {
        return memberRepository.findByRoomIdAndUserId(roomId, userId)
                .orElseThrow(() -> new NotRoomMemberException("Not a member of this room"));
    }

    private void validateModeratorRole(ChatRoomMember member) {
        if (member.getRole() == MemberRole.MEMBER) {
            throw new IllegalStateException("Insufficient permissions");
        }
    }

    private void validateCallerOutranksTarget(ChatRoomMember caller, ChatRoomMember target) {
        if (caller.getRole() == MemberRole.OWNER) return;
        if (target.getRole() == MemberRole.OWNER) {
            throw new IllegalStateException("Cannot perform this action on the room owner");
        }
    }

}
