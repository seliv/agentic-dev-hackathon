import { useState } from 'react';
import { EditOutlined, DeleteOutlined, MessageOutlined } from '@ant-design/icons';
import { Popconfirm } from 'antd';
import type { ChatMessage, AttachmentInfo } from '../api/types.ts';
import { AttachmentPreview } from './AttachmentPreview.tsx';
import { ImageViewer } from './ImageViewer.tsx';

interface MessageBubbleProps {
  message: ChatMessage;
  isOwn: boolean;
  isRoomOwner: boolean;
  isRoomAdmin?: boolean;
  onReply?: (message: ChatMessage) => void;
  onEdit?: (message: ChatMessage) => void;
  onDelete?: (messageId: string) => void;
  onReplyClick?: (messageId: string) => void;
}

export const MessageBubble = ({ message, isOwn, isRoomOwner, isRoomAdmin, onReply, onEdit, onDelete, onReplyClick }: MessageBubbleProps) => {
  const [hovered, setHovered] = useState(false);
  const [viewingImage, setViewingImage] = useState<AttachmentInfo | null>(null);

  const time = new Date(message.createdAt).toLocaleTimeString([], {
    hour: '2-digit',
    minute: '2-digit',
  });

  if (message.deleted) {
    return (
      <div style={{ display: 'flex', justifyContent: isOwn ? 'flex-end' : 'flex-start', marginBottom: 8 }}>
        <div style={{
          maxWidth: '70%',
          padding: '8px 12px',
          borderRadius: 12,
          background: '#f5f5f5',
          color: 'rgba(0,0,0,0.3)',
          fontStyle: 'italic',
          fontSize: 13,
        }}>
          This message was deleted
        </div>
      </div>
    );
  }

  const showActions = hovered && !message.deleted;
  const canEdit = isOwn && onEdit;
  const canDelete = (isOwn || isRoomOwner || isRoomAdmin) && onDelete;

  return (
    <div
      style={{ display: 'flex', justifyContent: isOwn ? 'flex-end' : 'flex-start', marginBottom: 8, position: 'relative' }}
      onMouseEnter={() => setHovered(true)}
      onMouseLeave={() => setHovered(false)}
    >
      <div style={{
        maxWidth: '70%',
        padding: '8px 12px',
        borderRadius: 12,
        background: isOwn ? '#1677ff' : '#f0f0f0',
        color: isOwn ? '#fff' : '#000',
      }}>
        {!isOwn && (
          <div style={{ fontSize: 12, color: '#1677ff', fontWeight: 500, marginBottom: 2 }}>
            {message.senderDisplayName || message.senderUsername}
          </div>
        )}

        {message.replyToPreview && (
          <div
            onClick={() => message.replyToPreview && onReplyClick?.(message.replyToPreview.messageId)}
            style={{
              padding: '4px 8px',
              marginBottom: 4,
              borderLeft: '3px solid ' + (isOwn ? 'rgba(255,255,255,0.5)' : '#1677ff'),
              borderRadius: 4,
              background: isOwn ? 'rgba(255,255,255,0.15)' : 'rgba(0,0,0,0.05)',
              cursor: 'pointer',
              fontSize: 12,
            }}
          >
            <div style={{ fontWeight: 500, opacity: 0.8 }}>
              {message.replyToPreview.senderDisplayName || message.replyToPreview.senderUsername}
            </div>
            <div style={{ opacity: 0.7 }}>
              {message.replyToPreview.deleted ? '[deleted message]' : message.replyToPreview.content}
            </div>
          </div>
        )}

        {message.content && (
          <div style={{ whiteSpace: 'pre-wrap', wordBreak: 'break-word' }}>
            {message.content}
          </div>
        )}

        {message.attachments && message.attachments.length > 0 && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 4, marginTop: message.content ? 4 : 0 }}>
            {message.attachments.map(att => (
              <AttachmentPreview
                key={att.id}
                attachment={att}
                onImageClick={() => att.contentType.startsWith('image/') && setViewingImage(att)}
              />
            ))}
          </div>
        )}

        <div style={{
          fontSize: 11,
          color: isOwn ? 'rgba(255,255,255,0.7)' : 'rgba(0,0,0,0.4)',
          textAlign: 'right',
          marginTop: 4,
        }}>
          {message.editedAt && <span style={{ marginRight: 4 }}>(edited)</span>}
          {time}
        </div>
      </div>

      {showActions && (
        <div style={{
          display: 'flex',
          gap: 2,
          position: 'absolute',
          top: -8,
          [isOwn ? 'left' : 'right']: 0,
          background: '#fff',
          borderRadius: 4,
          boxShadow: '0 1px 3px rgba(0,0,0,0.15)',
          padding: '2px 4px',
        }}>
          {onReply && (
            <MessageOutlined
              onClick={() => onReply(message)}
              style={{ fontSize: 14, cursor: 'pointer', padding: 4, color: '#666' }}
            />
          )}
          {canEdit && (
            <EditOutlined
              onClick={() => onEdit!(message)}
              style={{ fontSize: 14, cursor: 'pointer', padding: 4, color: '#666' }}
            />
          )}
          {canDelete && (
            <Popconfirm title="Delete this message?" onConfirm={() => onDelete!(message.id)} okText="Delete" okType="danger">
              <DeleteOutlined style={{ fontSize: 14, cursor: 'pointer', padding: 4, color: '#ff4d4f' }} />
            </Popconfirm>
          )}
        </div>
      )}

      <ImageViewer attachment={viewingImage} onClose={() => setViewingImage(null)} />
    </div>
  );
};
