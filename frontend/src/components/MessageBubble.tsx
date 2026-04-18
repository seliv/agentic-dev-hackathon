import type { ChatMessage } from '../api/types.ts';

interface MessageBubbleProps {
  message: ChatMessage;
  isOwn: boolean;
}

export const MessageBubble = ({ message, isOwn }: MessageBubbleProps) => {
  const time = new Date(message.createdAt).toLocaleTimeString([], {
    hour: '2-digit',
    minute: '2-digit',
  });

  return (
    <div style={{
      display: 'flex',
      justifyContent: isOwn ? 'flex-end' : 'flex-start',
      marginBottom: 8,
    }}>
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
        <div style={{ whiteSpace: 'pre-wrap', wordBreak: 'break-word' }}>
          {message.content}
        </div>
        <div style={{
          fontSize: 11,
          color: isOwn ? 'rgba(255,255,255,0.7)' : 'rgba(0,0,0,0.4)',
          textAlign: 'right',
          marginTop: 4,
        }}>
          {time}
        </div>
      </div>
    </div>
  );
};
