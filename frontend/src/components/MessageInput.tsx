import { useState } from 'react';
import { Input, Button } from 'antd';
import { SendOutlined } from '@ant-design/icons';

interface MessageInputProps {
  onSend: (content: string) => void;
  disabled?: boolean;
}

export const MessageInput = ({ onSend, disabled }: MessageInputProps) => {
  const [value, setValue] = useState('');

  const handleSend = () => {
    const trimmed = value.trim();
    if (!trimmed) return;
    onSend(trimmed);
    setValue('');
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  return (
    <div style={{ display: 'flex', gap: 8, padding: '12px 16px', borderTop: '1px solid #f0f0f0' }}>
      <Input.TextArea
        value={value}
        onChange={(e) => setValue(e.target.value)}
        onKeyDown={handleKeyDown}
        placeholder="Type a message..."
        autoSize={{ minRows: 1, maxRows: 4 }}
        disabled={disabled}
        style={{ flex: 1 }}
      />
      <Button
        type="primary"
        icon={<SendOutlined />}
        onClick={handleSend}
        disabled={!value.trim() || disabled}
      />
    </div>
  );
};
