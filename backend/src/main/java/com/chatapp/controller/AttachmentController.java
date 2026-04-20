package com.chatapp.controller;

import com.chatapp.entity.Attachment;
import com.chatapp.service.AttachmentService;
import com.chatapp.service.ChatRoomService;
import com.chatapp.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/attachments")
@RequiredArgsConstructor
public class AttachmentController {

    private final AttachmentService attachmentService;
    private final ChatRoomService chatRoomService;
    private final FileStorageService fileStorageService;

    @GetMapping("/{attachmentId}")
    public ResponseEntity<Resource> download(
            @PathVariable UUID attachmentId,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        Attachment attachment = attachmentService.getAttachment(attachmentId);

        chatRoomService.validateMembership(attachment.getMessage().getRoom().getId(), userId);

        if (attachment.getMessage().getDeletedAt() != null) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = fileStorageService.load(attachment.getStoragePath());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(attachment.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + attachment.getOriginalFileName() + "\"")
                .body(resource);
    }

    @GetMapping("/{attachmentId}/thumbnail")
    public ResponseEntity<Resource> getThumbnail(
            @PathVariable UUID attachmentId,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        Attachment attachment = attachmentService.getAttachment(attachmentId);

        chatRoomService.validateMembership(attachment.getMessage().getRoom().getId(), userId);

        if (attachment.getMessage().getDeletedAt() != null) {
            return ResponseEntity.notFound().build();
        }

        if (!attachment.getContentType().startsWith("image/")) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = fileStorageService.load(attachment.getStoragePath());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(attachment.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + attachment.getOriginalFileName() + "\"")
                .body(resource);
    }

}
