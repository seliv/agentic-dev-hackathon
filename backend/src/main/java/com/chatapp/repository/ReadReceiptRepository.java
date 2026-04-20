package com.chatapp.repository;

import com.chatapp.entity.ReadReceipt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReadReceiptRepository extends JpaRepository<ReadReceipt, Long> {

    Optional<ReadReceipt> findByUserIdAndRoomId(Long userId, UUID roomId);

    @Query("""
        SELECT rr FROM ReadReceipt rr
        WHERE rr.user.id = :userId AND rr.room.id IN :roomIds
    """)
    List<ReadReceipt> findByUserIdAndRoomIdIn(Long userId, List<UUID> roomIds);

}
