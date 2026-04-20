import { Button, Typography } from 'antd';
import { LogoutOutlined, UserAddOutlined } from '@ant-design/icons';
import type { ChatRoom, PresenceStatus } from '../api/types.ts';
import { PresenceIndicator } from './PresenceIndicator.tsx';

const { Text, Title } = Typography;

interface Props {
  room: ChatRoom;
  currentUserId: number;
  onLeave: () => void;
  onInvite?: () => void;
  dmPresence?: PresenceStatus;
}

export const RoomHeader = ({ room, currentUserId, onLeave, onInvite, dmPresence }: Props) => {
  const isOwner = room.ownerId === currentUserId;
  const isDirect = room.type === 'DIRECT';
  const isPrivate = room.type === 'PRIVATE';

  const displayName = isDirect
    ? 'Direct Message'
    : `${isPrivate ? '🔒 ' : '#'}${room.name}`;

  return (
    <div style={{
      display: 'flex',
      justifyContent: 'space-between',
      alignItems: 'center',
      padding: '12px 16px',
      borderBottom: '1px solid #f0f0f0',
    }}>
      <div>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <Title level={5} style={{ margin: 0 }}>{displayName}</Title>
          {dmPresence && <PresenceIndicator status={dmPresence} size={10} />}
        </div>
        {room.description && <Text type="secondary">{room.description}</Text>}
      </div>
      <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
        <Text type="secondary">{room.memberCount} members</Text>
        {isPrivate && onInvite && (
          <Button size="small" icon={<UserAddOutlined />} onClick={onInvite}>Invite</Button>
        )}
        {!isOwner && !isDirect && (
          <Button size="small" icon={<LogoutOutlined />} onClick={onLeave}>Leave</Button>
        )}
      </div>
    </div>
  );
};
