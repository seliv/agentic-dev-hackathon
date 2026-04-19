package com.chatapp.repository;

import com.chatapp.entity.InvitationStatus;
import com.chatapp.entity.RoomInvitation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RoomInvitationRepository extends JpaRepository<RoomInvitation, Long> {

    boolean existsByRoomIdAndInviteeIdAndStatus(UUID roomId, Long inviteeId, InvitationStatus status);

    List<RoomInvitation> findByInviteeIdAndStatus(Long inviteeId, InvitationStatus status);

}
