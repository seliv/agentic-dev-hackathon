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
        return sendMessage(room, sender, content, null);
    }

    @Transactional
    public Message sendMessage(ChatRoom room, User sender, String content, UUID replyToId) {
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

        if (replyToId != null) {
            Message replyTo = messageRepository.findById(replyToId)
                    .orElseThrow(() -> new IllegalArgumentException("Reply-to message not found"));
            if (!replyTo.getRoom().getId().equals(room.getId())) {
                throw new IllegalArgumentException("Reply-to message must be in the same room");
            }
            message.setReplyTo(replyTo);
        }

        return messageRepository.save(message);
    }

    @Transactional
    public Message editMessage(UUID messageId, Long userId, String newContent) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("Message not found"));

        if (!message.getSender().getId().equals(userId)) {
            throw new IllegalStateException("Only the sender can edit a message");
        }

        if (message.getDeletedAt() != null) {
            throw new IllegalStateException("Cannot edit a deleted message");
        }

        message.setContent(newContent);
        message.setEditedAt(Instant.now());
        return messageRepository.save(message);
    }

    @Transactional
    public Message deleteMessage(UUID messageId, Long userId, UUID roomId) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("Message not found"));

        if (!message.getRoom().getId().equals(roomId)) {
            throw new IllegalArgumentException("Message does not belong to this room");
        }

        boolean isSender = message.getSender().getId().equals(userId);
        boolean isOwner = message.getRoom().getOwner().getId().equals(userId);

        if (!isSender && !isOwner) {
            throw new IllegalStateException("Only the sender or room owner can delete a message");
        }

        message.setDeletedAt(Instant.now());
        return messageRepository.save(message);
    }

    public Message findById(UUID messageId) {
        return messageRepository.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("Message not found"));
    }

    public List<Message> getMessages(UUID roomId, Instant before, int limit) {
        PageRequest pageable = PageRequest.of(0, limit);
        if (before != null) {
            return messageRepository.findByRoomIdAndCreatedAtBeforeOrderByCreatedAtDesc(roomId, before, pageable);
        }
        return messageRepository.findByRoomIdOrderByCreatedAtDesc(roomId, pageable);
    }

}
