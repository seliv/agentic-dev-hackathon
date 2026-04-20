package com.chatapp.dto;

import com.chatapp.entity.Message;
import lombok.Data;

import java.util.UUID;

@Data
public class ReplyPreview {

    private UUID messageId;
    private Long senderId;
    private String senderUsername;
    private String senderDisplayName;
    private String content;
    private boolean deleted;

    public static ReplyPreview fromEntity(Message message) {
        ReplyPreview preview = new ReplyPreview();
        preview.setMessageId(message.getId());
        preview.setSenderId(message.getSender().getId());
        preview.setSenderUsername(message.getSender().getUsername());
        preview.setSenderDisplayName(message.getSender().getDisplayName());
        preview.setDeleted(message.getDeletedAt() != null);
        if (!preview.isDeleted()) {
            String text = message.getContent();
            preview.setContent(text.length() > 100 ? text.substring(0, 100) + "..." : text);
        }
        return preview;
    }

}
