package com.chatapp.repository;

import com.chatapp.entity.UserBlock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface UserBlockRepository extends JpaRepository<UserBlock, Long> {

    Optional<UserBlock> findByBlockerIdAndBlockedId(Long blockerId, Long blockedId);

    boolean existsByBlockerIdAndBlockedId(Long blockerId, Long blockedId);

    @Query("SELECT CASE WHEN COUNT(b) > 0 THEN true ELSE false END FROM UserBlock b WHERE " +
           "(b.blocker.id = :userIdA AND b.blocked.id = :userIdB) OR " +
           "(b.blocker.id = :userIdB AND b.blocked.id = :userIdA)")
    boolean isBlockedEitherDirection(Long userIdA, Long userIdB);

    List<UserBlock> findByBlockerId(Long blockerId);

}
