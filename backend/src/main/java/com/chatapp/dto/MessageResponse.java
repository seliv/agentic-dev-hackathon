package com.chatapp.dto;

import com.chatapp.entity.Attachment;
import com.chatapp.entity.Message;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
public class MessageResponse {

    private UUID id;
    private UUID roomId;
    private Long senderId;
    private String senderUsername;
    private String senderDisplayName;
    private String content;
    private UUID replyToId;
    private ReplyPreview replyToPreview;
    private Instant editedAt;
    private boolean deleted;
    private List<AttachmentResponse> attachments;
    private Instant createdAt;
    private Instant updatedAt;

    public static MessageResponse fromEntity(Message message) {
        return fromEntity(message, message.getAttachments());
    }

    public static MessageResponse fromEntity(Message message, List<Attachment> attachments) {
        MessageResponse response = new MessageResponse();
        response.setId(message.getId());
        response.setRoomId(message.getRoom().getId());
        response.setSenderId(message.getSender().getId());
        response.setSenderUsername(message.getSender().getUsername());
        response.setSenderDisplayName(message.getSender().getDisplayName());
        response.setCreatedAt(message.getCreatedAt());
        response.setUpdatedAt(message.getUpdatedAt());
        response.setEditedAt(message.getEditedAt());

        boolean isDeleted = message.getDeletedAt() != null;
        response.setDeleted(isDeleted);

        if (isDeleted) {
            response.setContent(null);
            response.setAttachments(List.of());
            response.setReplyToPreview(null);
        } else {
            response.setContent(message.getContent());
            response.setAttachments(attachments != null
                    ? attachments.stream().map(AttachmentResponse::fromEntity).toList()
                    : List.of());

            if (message.getReplyTo() != null) {
                response.setReplyToId(message.getReplyTo().getId());
                response.setReplyToPreview(ReplyPreview.fromEntity(message.getReplyTo()));
            }
        }

        return response;
    }

}
