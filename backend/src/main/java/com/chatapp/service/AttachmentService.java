package com.chatapp.service;

import com.chatapp.entity.Attachment;
import com.chatapp.entity.Message;
import com.chatapp.repository.AttachmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AttachmentService {

    private final AttachmentRepository attachmentRepository;
    private final FileStorageService fileStorageService;

    @Transactional
    public List<Attachment> createAttachments(Message message, List<MultipartFile> files) {
        if (files.size() > 5) {
            throw new IllegalArgumentException("Maximum 5 files per message");
        }

        List<Attachment> attachments = new ArrayList<>();
        for (MultipartFile file : files) {
            String storagePath = fileStorageService.store(file);

            Attachment attachment = new Attachment();
            attachment.setMessage(message);
            attachment.setFileName(storagePath.substring(storagePath.lastIndexOf('/') + 1));
            attachment.setOriginalFileName(file.getOriginalFilename());
            attachment.setContentType(file.getContentType());
            attachment.setFileSize(file.getSize());
            attachment.setStoragePath(storagePath);
            attachments.add(attachmentRepository.save(attachment));
        }
        return attachments;
    }

    public Attachment getAttachment(UUID attachmentId) {
        return attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new IllegalArgumentException("Attachment not found"));
    }

    public List<Attachment> getAttachmentsByMessageId(UUID messageId) {
        return attachmentRepository.findByMessageId(messageId);
    }

}
