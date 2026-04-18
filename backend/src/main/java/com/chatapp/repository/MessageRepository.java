package com.chatapp.repository;

import com.chatapp.entity.Message;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface MessageRepository extends JpaRepository<Message, UUID> {

    List<Message> findByRoomIdAndCreatedAtBeforeOrderByCreatedAtDesc(UUID roomId, Instant before, Pageable pageable);

    List<Message> findByRoomIdOrderByCreatedAtDesc(UUID roomId, Pageable pageable);

}
