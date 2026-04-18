import { Button, Typography } from 'antd';
import { LogoutOutlined, TeamOutlined } from '@ant-design/icons';
import type { ChatRoom } from '../api/types.ts';

const { Text } = Typography;

interface RoomHeaderProps {
  room: ChatRoom;
  currentUserId: number;
  onLeave: () => void;
}

export const RoomHeader = ({ room, currentUserId, onLeave }: RoomHeaderProps) => {
  const isOwner = room.ownerId === currentUserId;

  return (
    <div style={{
      display: 'flex',
      justifyContent: 'space-between',
      alignItems: 'center',
      padding: '12px 16px',
      borderBottom: '1px solid #f0f0f0',
    }}>
      <div>
        <Text strong style={{ fontSize: 16 }}>#{room.name}</Text>
        {room.description && (
          <Text type="secondary" style={{ marginLeft: 12 }}>{room.description}</Text>
        )}
      </div>
      <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
        <Text type="secondary"><TeamOutlined /> {room.memberCount}</Text>
        {!isOwner && (
          <Button size="small" danger icon={<LogoutOutlined />} onClick={onLeave}>
            Leave
          </Button>
        )}
      </div>
    </div>
  );
};
