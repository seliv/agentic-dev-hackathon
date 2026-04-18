package com.chatapp.repository;

import com.chatapp.entity.ChatRoom;
import com.chatapp.entity.ChatRoomMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChatRoomMemberRepository extends JpaRepository<ChatRoomMember, Long> {

    boolean existsByRoomIdAndUserId(UUID roomId, Long userId);

    Optional<ChatRoomMember> findByRoomIdAndUserId(UUID roomId, Long userId);

    List<ChatRoomMember> findByRoomId(UUID roomId);

    @Query("SELECT m.room FROM ChatRoomMember m WHERE m.user.id = :userId")
    List<ChatRoom> findRoomsByUserId(Long userId);

    long countByRoomId(UUID roomId);

}
