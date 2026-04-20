package com.chatapp.dto;

import com.chatapp.entity.Attachment;
import lombok.Data;

import java.util.UUID;

@Data
public class AttachmentResponse {

    private UUID id;
    private String originalFileName;
    private String contentType;
    private Long fileSize;
    private String downloadUrl;
    private String thumbnailUrl;

    public static AttachmentResponse fromEntity(Attachment attachment) {
        AttachmentResponse response = new AttachmentResponse();
        response.setId(attachment.getId());
        response.setOriginalFileName(attachment.getOriginalFileName());
        response.setContentType(attachment.getContentType());
        response.setFileSize(attachment.getFileSize());
        response.setDownloadUrl("/api/attachments/" + attachment.getId());
        if (attachment.getContentType().startsWith("image/")) {
            response.setThumbnailUrl("/api/attachments/" + attachment.getId() + "/thumbnail");
        }
        return response;
    }

}
