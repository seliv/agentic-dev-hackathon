package com.chatapp.service;

import com.chatapp.dto.CreateRoomRequest;
import com.chatapp.entity.ChatRoom;
import com.chatapp.entity.ChatRoomMember;
import com.chatapp.entity.MemberRole;
import com.chatapp.entity.RoomType;
import com.chatapp.entity.User;
import com.chatapp.exception.NotRoomMemberException;
import com.chatapp.exception.RoomNameAlreadyExistsException;
import com.chatapp.exception.RoomNotFoundException;
import com.chatapp.repository.ChatRoomMemberRepository;
import com.chatapp.repository.ChatRoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChatRoomService {

    private final ChatRoomRepository roomRepository;
    private final ChatRoomMemberRepository memberRepository;

    @Transactional
    public ChatRoom createRoom(CreateRoomRequest request, User owner) {
        RoomType type = RoomType.PUBLIC;
        if (request.getType() != null) {
            type = RoomType.valueOf(request.getType().toUpperCase());
        }

        if (type == RoomType.PUBLIC && roomRepository.existsByNameAndType(request.getName(), RoomType.PUBLIC)) {
            throw new RoomNameAlreadyExistsException("A public room with this name already exists");
        }

        ChatRoom room = new ChatRoom();
        room.setName(request.getName());
        room.setDescription(request.getDescription());
        room.setType(type);
        room.setOwner(owner);
        room = roomRepository.save(room);

        ChatRoomMember membership = new ChatRoomMember();
        membership.setRoom(room);
        membership.setUser(owner);
        membership.setRole(MemberRole.OWNER);
        memberRepository.save(membership);

        return room;
    }

    @Transactional
    public ChatRoom findOrCreateDirectRoom(User userA, User userB) {
        Long minId = Math.min(userA.getId(), userB.getId());
        Long maxId = Math.max(userA.getId(), userB.getId());
        String dmName = "dm-" + minId + "-" + maxId;

        return roomRepository.findDirectRoomByName(dmName).orElseGet(() -> {
            ChatRoom room = new ChatRoom();
            room.setName(dmName);
            room.setType(RoomType.DIRECT);
            room.setOwner(userA);
            room = roomRepository.save(room);

            ChatRoomMember memberA = new ChatRoomMember();
            memberA.setRoom(room);
            memberA.setUser(userA);
            memberA.setRole(MemberRole.MEMBER);
            memberRepository.save(memberA);

            ChatRoomMember memberB = new ChatRoomMember();
            memberB.setRoom(room);
            memberB.setUser(userB);
            memberB.setRole(MemberRole.MEMBER);
            memberRepository.save(memberB);

            return room;
        });
    }

    public ChatRoom findById(UUID roomId) {
        return roomRepository.findById(roomId)
                .orElseThrow(() -> new RoomNotFoundException("Room not found"));
    }

    public List<ChatRoom> getUserRooms(Long userId) {
        return memberRepository.findRoomsByUserId(userId);
    }

    public Page<ChatRoom> getPublicRooms(String search, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size);
        if (search != null && !search.isBlank()) {
            return roomRepository.searchPublicRooms(search, pageable);
        }
        return roomRepository.findAllPublicRooms(pageable);
    }

    @Transactional
    public ChatRoomMember joinRoom(UUID roomId, User user) {
        ChatRoom room = findById(roomId);

        if (memberRepository.existsByRoomIdAndUserId(roomId, user.getId())) {
            return memberRepository.findByRoomIdAndUserId(roomId, user.getId()).get();
        }

        ChatRoomMember membership = new ChatRoomMember();
        membership.setRoom(room);
        membership.setUser(user);
        membership.setRole(MemberRole.MEMBER);
        return memberRepository.save(membership);
    }

    @Transactional
    public void leaveRoom(UUID roomId, Long userId) {
        ChatRoom room = findById(roomId);

        if (room.getOwner().getId().equals(userId)) {
            throw new IllegalStateException("Room owner cannot leave the room");
        }

        ChatRoomMember membership = memberRepository.findByRoomIdAndUserId(roomId, userId)
                .orElseThrow(() -> new NotRoomMemberException("Not a member of this room"));
        memberRepository.delete(membership);
    }

    public List<ChatRoomMember> getMembers(UUID roomId) {
        findById(roomId);
        return memberRepository.findByRoomId(roomId);
    }

    public void validateMembership(UUID roomId, Long userId) {
        if (!memberRepository.existsByRoomIdAndUserId(roomId, userId)) {
            throw new NotRoomMemberException("Not a member of this room");
        }
    }

    public long getMemberCount(UUID roomId) {
        return memberRepository.countByRoomId(roomId);
    }

}
