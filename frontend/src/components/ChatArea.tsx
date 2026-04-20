import { useState, useEffect, useRef, useCallback } from 'react';
import { Spin, Typography } from 'antd';
import { MessageBubble } from './MessageBubble.tsx';
import type { ChatMessage } from '../api/types.ts';

const { Text } = Typography;

interface ChatAreaProps {
  messages: ChatMessage[];
  currentUserId: number;
  roomOwnerId: number;
  isRoomAdmin?: boolean;
  onLoadMore: () => Promise<boolean>;
  loading?: boolean;
  onReply: (message: ChatMessage) => void;
  onEdit: (message: ChatMessage) => void;
  onDelete: (messageId: string) => void;
  onFileDrop: (files: File[]) => void;
}

export const ChatArea = ({ messages, currentUserId, roomOwnerId, isRoomAdmin, onLoadMore, loading, onReply, onEdit, onDelete, onFileDrop }: ChatAreaProps) => {
  const containerRef = useRef<HTMLDivElement>(null);
  const isAtBottomRef = useRef(true);
  const prevMessagesLengthRef = useRef(0);
  const loadingRef = useRef(false);
  const [dragging, setDragging] = useState(false);

  const checkIfAtBottom = () => {
    const el = containerRef.current;
    if (!el) return;
    isAtBottomRef.current = el.scrollHeight - el.scrollTop - el.clientHeight < 50;
  };

  const scrollToBottom = () => {
    const el = containerRef.current;
    if (el) el.scrollTop = el.scrollHeight;
  };

  const scrollToMessage = (messageId: string) => {
    const el = document.getElementById(`msg-${messageId}`);
    if (el) {
      el.scrollIntoView({ behavior: 'smooth', block: 'center' });
      el.style.transition = 'background 0.3s';
      el.style.background = '#fff7e6';
      setTimeout(() => { el.style.background = 'transparent'; }, 1500);
    }
  };

  useEffect(() => {
    if (messages.length > prevMessagesLengthRef.current) {
      const addedToEnd = prevMessagesLengthRef.current === 0 || isAtBottomRef.current;
      if (addedToEnd) {
        scrollToBottom();
      }
    }
    prevMessagesLengthRef.current = messages.length;
  }, [messages]);

  const handleScroll = useCallback(async () => {
    checkIfAtBottom();
    const el = containerRef.current;
    if (!el || loadingRef.current) return;
    if (el.scrollTop < 100) {
      loadingRef.current = true;
      const prevScrollHeight = el.scrollHeight;
      try {
        await onLoadMore();
        requestAnimationFrame(() => {
          el.scrollTop = el.scrollHeight - prevScrollHeight;
        });
      } finally {
        loadingRef.current = false;
      }
    }
  }, [onLoadMore]);

  const handleDragOver = (e: React.DragEvent) => {
    e.preventDefault();
    setDragging(true);
  };

  const handleDragLeave = (e: React.DragEvent) => {
    if (e.currentTarget === e.target) {
      setDragging(false);
    }
  };

  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault();
    setDragging(false);
    const droppedFiles = Array.from(e.dataTransfer.files);
    if (droppedFiles.length > 0) {
      onFileDrop(droppedFiles);
    }
  };

  if (messages.length === 0 && !loading) {
    return (
      <div
        onDragOver={handleDragOver}
        onDragLeave={handleDragLeave}
        onDrop={handleDrop}
        style={{
          flex: 1,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          color: 'rgba(0,0,0,0.4)',
          position: 'relative',
        }}
      >
        <Text type="secondary">No messages yet. Start the conversation!</Text>
        {dragging && <DropOverlay />}
      </div>
    );
  }

  return (
    <div
      ref={containerRef}
      onScroll={handleScroll}
      onDragOver={handleDragOver}
      onDragLeave={handleDragLeave}
      onDrop={handleDrop}
      style={{
        flex: 1,
        overflowY: 'auto',
        padding: '16px',
        position: 'relative',
      }}
    >
      {loading && (
        <div style={{ textAlign: 'center', padding: 8 }}>
          <Spin size="small" />
        </div>
      )}
      {messages.map((msg) => (
        <div key={msg.id} id={`msg-${msg.id}`}>
          <MessageBubble
            message={msg}
            isOwn={msg.senderId === currentUserId}
            isRoomOwner={roomOwnerId === currentUserId}
            isRoomAdmin={isRoomAdmin}
            onReply={onReply}
            onEdit={onEdit}
            onDelete={onDelete}
            onReplyClick={scrollToMessage}
          />
        </div>
      ))}
      {dragging && <DropOverlay />}
    </div>
  );
};

const DropOverlay = () => (
  <div style={{
    position: 'absolute',
    inset: 0,
    background: 'rgba(22, 119, 255, 0.08)',
    border: '2px dashed #1677ff',
    borderRadius: 8,
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    zIndex: 10,
    pointerEvents: 'none',
  }}>
    <Text style={{ fontSize: 16, color: '#1677ff' }}>Drop files here</Text>
  </div>
);
