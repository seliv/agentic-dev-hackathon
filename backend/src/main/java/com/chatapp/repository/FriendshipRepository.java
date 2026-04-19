package com.chatapp.repository;

import com.chatapp.entity.Friendship;
import com.chatapp.entity.FriendshipStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface FriendshipRepository extends JpaRepository<Friendship, Long> {

    @Query("SELECT f FROM Friendship f WHERE " +
           "((f.requester.id = :userIdA AND f.addressee.id = :userIdB) OR " +
           "(f.requester.id = :userIdB AND f.addressee.id = :userIdA))")
    Optional<Friendship> findBetweenUsers(Long userIdA, Long userIdB);

    @Query("SELECT f FROM Friendship f WHERE " +
           "(f.requester.id = :userId OR f.addressee.id = :userId) AND f.status = :status")
    List<Friendship> findByUserIdAndStatus(Long userId, FriendshipStatus status);

    @Query("SELECT f FROM Friendship f WHERE " +
           "(f.requester.id = :userId OR f.addressee.id = :userId) AND f.status = 'PENDING'")
    List<Friendship> findPendingByUserId(Long userId);

    @Query("SELECT CASE WHEN COUNT(f) > 0 THEN true ELSE false END FROM Friendship f WHERE " +
           "((f.requester.id = :userIdA AND f.addressee.id = :userIdB) OR " +
           "(f.requester.id = :userIdB AND f.addressee.id = :userIdA)) AND f.status = 'ACCEPTED'")
    boolean areFriends(Long userIdA, Long userIdB);

}
