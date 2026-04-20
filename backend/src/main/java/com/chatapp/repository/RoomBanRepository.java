package com.chatapp.repository;

import com.chatapp.entity.RoomBan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoomBanRepository extends JpaRepository<RoomBan, Long> {

    List<RoomBan> findByRoomId(UUID roomId);

    Optional<RoomBan> findByRoomIdAndUserId(UUID roomId, Long userId);

    boolean existsByRoomIdAndUserId(UUID roomId, Long userId);

    void deleteByRoomIdAndUserId(UUID roomId, Long userId);

}
