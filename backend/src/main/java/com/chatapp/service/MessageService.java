package com.chatapp.service;

import com.chatapp.entity.ChatRoom;
import com.chatapp.entity.Message;
import com.chatapp.entity.User;
import com.chatapp.repository.MessageRepository;
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

    @Transactional
    public Message sendMessage(ChatRoom room, User sender, String content) {
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
