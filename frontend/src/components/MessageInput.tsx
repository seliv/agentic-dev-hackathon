import { useState, useRef, useEffect } from 'react';
import { Input, Button, Tag } from 'antd';
import { SendOutlined, PaperClipOutlined, CloseOutlined } from '@ant-design/icons';
import { EmojiPicker } from './EmojiPicker.tsx';
import type { ChatMessage } from '../api/types.ts';

interface MessageInputProps {
  onSend: (content: string, files: File[], replyToId?: string) => void;
  onCancelReply?: () => void;
  onCancelEdit?: () => void;
  replyTo?: ChatMessage | null;
  editingMessage?: ChatMessage | null;
  disabled?: boolean;
}

export const MessageInput = ({ onSend, onCancelReply, onCancelEdit, replyTo, editingMessage, disabled }: MessageInputProps) => {
  const [value, setValue] = useState('');
  const [files, setFiles] = useState<File[]>([]);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const isEditing = !!editingMessage;

  useEffect(() => {
    if (editingMessage?.content) {
      setValue(editingMessage.content);
    }
  }, [editingMessage]);

  useEffect(() => {
    if (!editingMessage && !replyTo) {
      setValue('');
    }
  }, [editingMessage, replyTo]);

  const handleSend = () => {
    const trimmed = value.trim();
    if (!trimmed && files.length === 0) return;

    if (isEditing) {
      onSend(trimmed, [], undefined);
    } else {
      onSend(trimmed, files, replyTo?.id);
    }

    setValue('');
    setFiles([]);
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  const handleFileSelect = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files) {
      addFiles(Array.from(e.target.files));
      e.target.value = '';
    }
  };

  const addFiles = (newFiles: File[]) => {
    setFiles(prev => {
      const combined = [...prev, ...newFiles];
      return combined.slice(0, 5);
    });
  };

  const removeFile = (index: number) => {
    setFiles(prev => prev.filter((_, i) => i !== index));
  };

  const handlePaste = (e: React.ClipboardEvent) => {
    const pastedFiles = Array.from(e.clipboardData.files);
    if (pastedFiles.length > 0) {
      e.preventDefault();
      addFiles(pastedFiles);
    }
  };

  const handleEmojiSelect = (emoji: string) => {
    setValue(prev => prev + emoji);
  };

  return (
    <div style={{ borderTop: '1px solid #f0f0f0' }}>
      {replyTo && !isEditing && (
        <div style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          padding: '6px 16px',
          background: '#f5f5f5',
          fontSize: 12,
          color: '#666',
        }}>
          <span>
            Replying to <strong>{replyTo.senderDisplayName || replyTo.senderUsername}</strong>:{' '}
            {replyTo.content?.substring(0, 50)}{(replyTo.content?.length || 0) > 50 ? '...' : ''}
          </span>
          <CloseOutlined onClick={onCancelReply} style={{ cursor: 'pointer', fontSize: 12 }} />
        </div>
      )}

      {isEditing && (
        <div style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          padding: '6px 16px',
          background: '#fff7e6',
          fontSize: 12,
          color: '#d48806',
        }}>
          <span>Editing message</span>
          <CloseOutlined onClick={onCancelEdit} style={{ cursor: 'pointer', fontSize: 12 }} />
        </div>
      )}

      {files.length > 0 && (
        <div style={{ display: 'flex', gap: 4, padding: '6px 16px', flexWrap: 'wrap' }}>
          {files.map((file, i) => (
            <Tag key={i} closable onClose={() => removeFile(i)}>
              {file.name.length > 20 ? file.name.substring(0, 20) + '...' : file.name}
            </Tag>
          ))}
        </div>
      )}

      <div style={{ display: 'flex', gap: 8, padding: '12px 16px', alignItems: 'flex-end' }}>
        {!isEditing && (
          <>
            <Button
              type="text"
              icon={<PaperClipOutlined />}
              onClick={() => fileInputRef.current?.click()}
              disabled={disabled || files.length >= 5}
            />
            <input
              ref={fileInputRef}
              type="file"
              multiple
              style={{ display: 'none' }}
              onChange={handleFileSelect}
            />
          </>
        )}
        <EmojiPicker onSelect={handleEmojiSelect} />
        <Input.TextArea
          value={value}
          onChange={(e) => setValue(e.target.value)}
          onKeyDown={handleKeyDown}
          onPaste={handlePaste}
          placeholder={isEditing ? 'Edit message...' : 'Type a message...'}
          autoSize={{ minRows: 1, maxRows: 4 }}
          disabled={disabled}
          style={{ flex: 1 }}
        />
        <Button
          type="primary"
          icon={<SendOutlined />}
          onClick={handleSend}
          disabled={(!value.trim() && files.length === 0) || disabled}
        />
      </div>
    </div>
  );
};
