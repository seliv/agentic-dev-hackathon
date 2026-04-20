import { Button, Typography } from 'antd';
import { PlusOutlined, SearchOutlined, TeamOutlined, UserOutlined } from '@ant-design/icons';
import type { ChatRoom } from '../api/types.ts';
import type { PresenceStatus } from '../api/types.ts';
import { UnreadBadge } from './UnreadBadge.tsx';
import { PresenceIndicator } from './PresenceIndicator.tsx';
import { useAuth } from '../contexts/AuthContext.tsx';

const { Text } = Typography;

interface RoomListProps {
  rooms: ChatRoom[];
  selectedRoomId: string | null;
  onSelectRoom: (room: ChatRoom) => void;
  onBrowse: () => void;
  onCreate: () => void;
  onContacts: () => void;
  onUserSearch: () => void;
  getUnreadCount: (roomId: string) => number;
  getPresence?: (userId: number) => PresenceStatus;
}

function extractOtherUserId(dmName: string, currentUserId: number): number | null {
  const match = dmName.match(/^dm-(\d+)-(\d+)$/);
  if (!match) return null;
  const id1 = Number(match[1]);
  const id2 = Number(match[2]);
  return id1 === currentUserId ? id2 : id1;
}

export const RoomList = ({ rooms, selectedRoomId, onSelectRoom, onBrowse, onCreate, onContacts, onUserSearch, getUnreadCount, getPresence }: RoomListProps) => {
  const { user } = useAuth();
  const chatRooms = rooms.filter(r => r.type !== 'DIRECT');
  const dmRooms = rooms.filter(r => r.type === 'DIRECT');

  const sortByUnread = (a: ChatRoom, b: ChatRoom) => {
    const unreadA = getUnreadCount(a.id);
    const unreadB = getUnreadCount(b.id);
    if (unreadA > 0 && unreadB === 0) return -1;
    if (unreadA === 0 && unreadB > 0) return 1;
    return 0;
  };

  const sortedChatRooms = [...chatRooms].sort(sortByUnread);
  const sortedDmRooms = [...dmRooms].sort(sortByUnread);

  return (
    <div style={{
      display: 'flex',
      flexDirection: 'column',
      height: '100%',
      borderRight: '1px solid #f0f0f0',
    }}>
      <div style={{ padding: '12px 16px', borderBottom: '1px solid #f0f0f0' }}>
        <Text strong style={{ fontSize: 16 }}>Rooms</Text>
      </div>
      <div style={{ flex: 1, overflowY: 'auto' }}>
        {sortedChatRooms.map((room) => (
          <div
            key={room.id}
            onClick={() => onSelectRoom(room)}
            style={{
              padding: '10px 16px',
              cursor: 'pointer',
              background: room.id === selectedRoomId ? '#e6f4ff' : 'transparent',
              borderBottom: '1px solid #f5f5f5',
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
            }}
          >
            <Text>{room.type === 'PRIVATE' ? '🔒' : '#'}{room.name}</Text>
            <UnreadBadge count={getUnreadCount(room.id)} />
          </div>
        ))}
        {sortedDmRooms.length > 0 && (
          <>
            <div style={{ padding: '12px 16px', borderBottom: '1px solid #f0f0f0', borderTop: '1px solid #f0f0f0' }}>
              <Text strong style={{ fontSize: 14 }}>Direct Messages</Text>
            </div>
            {sortedDmRooms.map((room) => {
              const otherUserId = user ? extractOtherUserId(room.name, user.id) : null;
              return (
                <div
                  key={room.id}
                  onClick={() => onSelectRoom(room)}
                  style={{
                    padding: '10px 16px',
                    cursor: 'pointer',
                    background: room.id === selectedRoomId ? '#e6f4ff' : 'transparent',
                    borderBottom: '1px solid #f5f5f5',
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'center',
                  }}
                >
                  <span style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                    {getPresence && otherUserId !== null && (
                      <PresenceIndicator status={getPresence(otherUserId)} />
                    )}
                    <Text><UserOutlined style={{ marginRight: 6 }} />{room.name.replace(/^dm-\d+-\d+$/, 'Direct Message')}</Text>
                  </span>
                  <UnreadBadge count={getUnreadCount(room.id)} />
                </div>
              );
            })}
          </>
        )}
      </div>
      <div style={{
        display: 'flex',
        flexDirection: 'column',
        gap: 4,
        padding: '8px 16px',
        borderTop: '1px solid #f0f0f0',
      }}>
        <div style={{ display: 'flex', gap: 8 }}>
          <Button icon={<SearchOutlined />} onClick={onBrowse} style={{ flex: 1 }}>Browse</Button>
          <Button type="primary" icon={<PlusOutlined />} onClick={onCreate} style={{ flex: 1 }}>Create</Button>
        </div>
        <div style={{ display: 'flex', gap: 8 }}>
          <Button icon={<TeamOutlined />} onClick={onContacts} style={{ flex: 1 }}>Contacts</Button>
          <Button icon={<UserOutlined />} onClick={onUserSearch} style={{ flex: 1 }}>Find Users</Button>
        </div>
      </div>
    </div>
  );
};
