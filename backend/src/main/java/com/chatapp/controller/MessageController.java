package com.chatapp.controller;

import com.chatapp.dto.*;
import com.chatapp.entity.Attachment;
import com.chatapp.entity.ChatRoom;
import com.chatapp.entity.Message;
import com.chatapp.entity.User;
import com.chatapp.service.AttachmentService;
import com.chatapp.service.ChatRoomService;
import com.chatapp.service.MessageService;
import com.chatapp.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/rooms/{roomId}/messages")
@RequiredArgsConstructor
public class MessageController {

    private final ChatRoomService chatRoomService;
    private final MessageService messageService;
    private final UserService userService;
    private final AttachmentService attachmentService;
    private final SimpMessagingTemplate messagingTemplate;

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<MessageResponse> postMessage(
            @PathVariable UUID roomId,
            @Valid @RequestBody SendMessageRequest request,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        chatRoomService.validateMembership(roomId, userId);
        User sender = userService.findById(userId);
        ChatRoom room = chatRoomService.findById(roomId);
        Message message = messageService.sendMessage(room, sender, request.getContent(), request.getReplyToId());
        MessageResponse response = MessageResponse.fromEntity(message, List.of());
        messagingTemplate.convertAndSend("/topic/rooms/" + roomId + "/messages", response);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MessageResponse> postMessageWithAttachments(
            @PathVariable UUID roomId,
            @RequestParam(required = false) String content,
            @RequestParam(required = false) UUID replyToId,
            @RequestPart(required = false) List<MultipartFile> files,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();

        if ((content == null || content.isBlank()) && (files == null || files.isEmpty())) {
            throw new IllegalArgumentException("At least content or files required");
        }

        chatRoomService.validateMembership(roomId, userId);
        User sender = userService.findById(userId);
        ChatRoom room = chatRoomService.findById(roomId);

        String messageContent = (content != null && !content.isBlank()) ? content : "";
        Message message = messageService.sendMessage(room, sender, messageContent, replyToId);

        List<Attachment> savedAttachments = List.of();
        if (files != null && !files.isEmpty()) {
            savedAttachments = attachmentService.createAttachments(message, files);
        }

        MessageResponse response = MessageResponse.fromEntity(message, savedAttachments);
        messagingTemplate.convertAndSend("/topic/rooms/" + roomId + "/messages", response);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<MessageResponse>> getMessages(
            @PathVariable UUID roomId,
            @RequestParam(required = false) Instant before,
            @RequestParam(defaultValue = "50") int limit,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        chatRoomService.validateMembership(roomId, userId);
        int cappedLimit = Math.min(limit, 100);
        List<MessageResponse> messages = messageService.getMessages(roomId, before, cappedLimit).stream()
                .map(MessageResponse::fromEntity)
                .toList();
        return ResponseEntity.ok(messages);
    }

    @PutMapping("/{messageId}")
    public ResponseEntity<MessageResponse> editMessage(
            @PathVariable UUID roomId,
            @PathVariable UUID messageId,
            @Valid @RequestBody EditMessageRequest request,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        chatRoomService.validateMembership(roomId, userId);
        Message message = messageService.editMessage(messageId, userId, request.getContent());
        MessageResponse response = MessageResponse.fromEntity(message);
        messagingTemplate.convertAndSend("/topic/rooms/" + roomId + "/events",
                new MessageEventResponse("MESSAGE_EDITED", response));
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{messageId}")
    public ResponseEntity<Void> deleteMessage(
            @PathVariable UUID roomId,
            @PathVariable UUID messageId,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        chatRoomService.validateMembership(roomId, userId);
        messageService.deleteMessage(messageId, userId, roomId);
        messagingTemplate.convertAndSend("/topic/rooms/" + roomId + "/events",
                new MessageEventResponse("MESSAGE_DELETED", Map.of("messageId", messageId)));
        return ResponseEntity.ok().build();
    }

}
