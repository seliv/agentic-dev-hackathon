package com.chatapp.service;

import com.chatapp.entity.ChatRoom;
import com.chatapp.entity.InvitationStatus;
import com.chatapp.entity.MemberRole;
import com.chatapp.entity.RoomInvitation;
import com.chatapp.entity.RoomType;
import com.chatapp.entity.User;
import com.chatapp.repository.ChatRoomMemberRepository;
import com.chatapp.repository.RoomInvitationRepository;
import com.chatapp.repository.UserBlockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RoomInvitationService {

    private final RoomInvitationRepository invitationRepository;
    private final ChatRoomService chatRoomService;
    private final ChatRoomMemberRepository memberRepository;
    private final UserBlockRepository userBlockRepository;
    private final UserService userService;
    private final SimpMessagingTemplate messagingTemplate;

    @Transactional
    public RoomInvitation inviteUser(java.util.UUID roomId, Long inviterId, Long inviteeId) {
        ChatRoom room = chatRoomService.findById(roomId);

        if (room.getType() != RoomType.PRIVATE) {
            throw new IllegalStateException("Can only invite to private rooms");
        }

        chatRoomService.validateMembership(roomId, inviterId);

        if (memberRepository.existsByRoomIdAndUserId(roomId, inviteeId)) {
            throw new IllegalStateException("User is already a member of this room");
        }

        if (invitationRepository.existsByRoomIdAndInviteeIdAndStatus(roomId, inviteeId, InvitationStatus.PENDING)) {
            throw new IllegalStateException("A pending invitation already exists for this user");
        }

        if (userBlockRepository.isBlockedEitherDirection(inviterId, inviteeId)) {
            throw new IllegalStateException("Cannot invite this user");
        }

        User inviter = userService.findById(inviterId);
        User invitee = userService.findById(inviteeId);

        RoomInvitation invitation = new RoomInvitation();
        invitation.setRoom(room);
        invitation.setInviter(inviter);
        invitation.setInvitee(invitee);
        invitation.setStatus(InvitationStatus.PENDING);
        invitation = invitationRepository.save(invitation);

        messagingTemplate.convertAndSendToUser(
                inviteeId.toString(),
                "/queue/notifications",
                Map.of("type", "ROOM_INVITATION", "data", Map.of(
                        "invitationId", invitation.getId(),
                        "roomId", roomId.toString(),
                        "roomName", room.getName(),
                        "inviterUsername", inviter.getUsername()
                ))
        );

        return invitation;
    }

    @Transactional
    public RoomInvitation acceptInvitation(Long invitationId, Long userId) {
        RoomInvitation invitation = invitationRepository.findById(invitationId)
                .orElseThrow(() -> new IllegalArgumentException("Invitation not found"));

        if (!invitation.getInvitee().getId().equals(userId)) {
            throw new IllegalStateException("Only the invitee can accept this invitation");
        }

        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new IllegalStateException("Invitation is not pending");
        }

        invitation.setStatus(InvitationStatus.ACCEPTED);
        invitation = invitationRepository.save(invitation);

        User invitee = userService.findById(userId);
        chatRoomService.joinRoom(invitation.getRoom().getId(), invitee);

        return invitation;
    }

    @Transactional
    public RoomInvitation declineInvitation(Long invitationId, Long userId) {
        RoomInvitation invitation = invitationRepository.findById(invitationId)
                .orElseThrow(() -> new IllegalArgumentException("Invitation not found"));

        if (!invitation.getInvitee().getId().equals(userId)) {
            throw new IllegalStateException("Only the invitee can decline this invitation");
        }

        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new IllegalStateException("Invitation is not pending");
        }

        invitation.setStatus(InvitationStatus.DECLINED);
        return invitationRepository.save(invitation);
    }

    public List<RoomInvitation> getPendingInvitations(Long userId) {
        return invitationRepository.findByInviteeIdAndStatus(userId, InvitationStatus.PENDING);
    }

}
