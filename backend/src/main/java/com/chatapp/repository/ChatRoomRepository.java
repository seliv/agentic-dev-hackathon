package com.chatapp.repository;

import com.chatapp.entity.ChatRoom;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.UUID;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, UUID> {

    boolean existsByName(String name);

    @Query("SELECT r FROM ChatRoom r WHERE r.type = 'PUBLIC' AND LOWER(r.name) LIKE LOWER(CONCAT('%', :search, '%'))")
    Page<ChatRoom> searchPublicRooms(String search, Pageable pageable);

    @Query("SELECT r FROM ChatRoom r WHERE r.type = 'PUBLIC'")
    Page<ChatRoom> findAllPublicRooms(Pageable pageable);

}
