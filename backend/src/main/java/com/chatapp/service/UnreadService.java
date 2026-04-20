package com.chatapp.service;

import com.chatapp.entity.ChatRoom;
import com.chatapp.entity.Message;
import com.chatapp.entity.ReadReceipt;
import com.chatapp.entity.User;
import com.chatapp.repository.ChatRoomMemberRepository;
import com.chatapp.repository.MessageRepository;
import com.chatapp.repository.ReadReceiptRepository;
import com.chatapp.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UnreadService {

    private final ReadReceiptRepository readReceiptRepository;
    private final MessageRepository messageRepository;
    private final ChatRoomMemberRepository memberRepository;
    private final UserRepository userRepository;

    @Transactional
    public void markAsRead(Long userId, UUID roomId, UUID lastReadMessageId) {
        Message message = messageRepository.findById(lastReadMessageId)
                .orElseThrow(() -> new IllegalArgumentException("Message not found"));

        ReadReceipt receipt = readReceiptRepository.findByUserIdAndRoomId(userId, roomId)
                .orElseGet(() -> {
                    ReadReceipt newReceipt = new ReadReceipt();
                    newReceipt.setUser(userRepository.getReferenceById(userId));
                    newReceipt.setRoom(message.getRoom());
                    return newReceipt;
                });

        receipt.setLastReadMessage(message);
        receipt.setLastReadAt(message.getCreatedAt());
        readReceiptRepository.save(receipt);
    }

    public Map<UUID, Long> getUnreadCounts(Long userId) {
        List<ChatRoom> rooms = memberRepository.findRoomsByUserId(userId);
        if (rooms.isEmpty()) return Map.of();

        List<UUID> roomIds = rooms.stream().map(ChatRoom::getId).toList();
        List<ReadReceipt> receipts = readReceiptRepository.findByUserIdAndRoomIdIn(userId, roomIds);

        Map<UUID, Instant> lastReadMap = new HashMap<>();
        for (ReadReceipt rr : receipts) {
            lastReadMap.put(rr.getRoom().getId(), rr.getLastReadAt());
        }

        Map<UUID, Long> counts = new HashMap<>();
        for (UUID roomId : roomIds) {
            Instant lastReadAt = lastReadMap.get(roomId);
            long count;
            if (lastReadAt != null) {
                count = messageRepository.countByRoomIdAndCreatedAtAfter(roomId, lastReadAt);
            } else {
                count = messageRepository.countByRoomId(roomId);
            }
            if (count > 0) {
                counts.put(roomId, count);
            }
        }

        return counts;
    }

}
