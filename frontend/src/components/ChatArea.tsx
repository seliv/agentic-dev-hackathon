import { useEffect, useRef, useCallback } from 'react';
import { Spin, Typography } from 'antd';
import { MessageBubble } from './MessageBubble.tsx';
import type { ChatMessage } from '../api/types.ts';

const { Text } = Typography;

interface ChatAreaProps {
  messages: ChatMessage[];
  currentUserId: number;
  onLoadMore: () => Promise<boolean>;
  loading?: boolean;
}

export const ChatArea = ({ messages, currentUserId, onLoadMore, loading }: ChatAreaProps) => {
  const containerRef = useRef<HTMLDivElement>(null);
  const isAtBottomRef = useRef(true);
  const prevMessagesLengthRef = useRef(0);
  const loadingRef = useRef(false);

  const checkIfAtBottom = () => {
    const el = containerRef.current;
    if (!el) return;
    isAtBottomRef.current = el.scrollHeight - el.scrollTop - el.clientHeight < 50;
  };

  const scrollToBottom = () => {
    const el = containerRef.current;
    if (el) el.scrollTop = el.scrollHeight;
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

  if (messages.length === 0 && !loading) {
    return (
      <div style={{
        flex: 1,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        color: 'rgba(0,0,0,0.4)',
      }}>
        <Text type="secondary">No messages yet. Start the conversation!</Text>
      </div>
    );
  }

  return (
    <div
      ref={containerRef}
      onScroll={handleScroll}
      style={{
        flex: 1,
        overflowY: 'auto',
        padding: '16px',
      }}
    >
      {loading && (
        <div style={{ textAlign: 'center', padding: 8 }}>
          <Spin size="small" />
        </div>
      )}
      {messages.map((msg) => (
        <MessageBubble
          key={msg.id}
          message={msg}
          isOwn={msg.senderId === currentUserId}
        />
      ))}
    </div>
  );
};
