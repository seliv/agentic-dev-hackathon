package com.chatapp.service;

import com.chatapp.entity.ChatRoom;
import com.chatapp.entity.ChatRoomMember;
import com.chatapp.entity.Message;
import com.chatapp.entity.RoomType;
import com.chatapp.entity.User;
import com.chatapp.repository.ChatRoomMemberRepository;
import com.chatapp.repository.MessageRepository;
import com.chatapp.repository.UserBlockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageRepository messageRepository;
    private final UserBlockRepository userBlockRepository;
    private final ChatRoomMemberRepository memberRepository;

    @Transactional
    public Message sendMessage(ChatRoom room, User sender, String content) {
        if (room.getType() == RoomType.DIRECT) {
            List<ChatRoomMember> members = memberRepository.findByRoomId(room.getId());
            for (ChatRoomMember member : members) {
                if (!member.getUser().getId().equals(sender.getId())) {
                    if (userBlockRepository.isBlockedEitherDirection(sender.getId(), member.getUser().getId())) {
                        throw new IllegalStateException("Cannot send messages in this conversation");
                    }
                }
            }
        }

        Message message = new Message();
        message.setRoom(room);
        message.setSender(sender);
        message.setContent(content);
        return messageRepository.save(message);
    }

    public List<Message> getMessages(UUID roomId, Instant before, int limit) {
        PageRequest pageable = PageRequest.of(0, limit);
        if (before != null) {
            return messageRepository.findByRoomIdAndCreatedAtBeforeOrderByCreatedAtDesc(roomId, before, pageable);
        }
        return messageRepository.findByRoomIdOrderByCreatedAtDesc(roomId, pageable);
    }

}
