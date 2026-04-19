import { Button, Typography } from 'antd';
import { PlusOutlined, SearchOutlined, TeamOutlined, UserOutlined } from '@ant-design/icons';
import type { ChatRoom } from '../api/types.ts';

const { Text } = Typography;

interface RoomListProps {
  rooms: ChatRoom[];
  selectedRoomId: string | null;
  onSelectRoom: (room: ChatRoom) => void;
  onBrowse: () => void;
  onCreate: () => void;
  onContacts: () => void;
  onUserSearch: () => void;
}

export const RoomList = ({ rooms, selectedRoomId, onSelectRoom, onBrowse, onCreate, onContacts, onUserSearch }: RoomListProps) => {
  const chatRooms = rooms.filter(r => r.type !== 'DIRECT');
  const dmRooms = rooms.filter(r => r.type === 'DIRECT');

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
        {chatRooms.map((room) => (
          <div
            key={room.id}
            onClick={() => onSelectRoom(room)}
            style={{
              padding: '10px 16px',
              cursor: 'pointer',
              background: room.id === selectedRoomId ? '#e6f4ff' : 'transparent',
              borderBottom: '1px solid #f5f5f5',
            }}
          >
            <Text>{room.type === 'PRIVATE' ? '🔒' : '#'}{room.name}</Text>
          </div>
        ))}
        {dmRooms.length > 0 && (
          <>
            <div style={{ padding: '12px 16px', borderBottom: '1px solid #f0f0f0', borderTop: '1px solid #f0f0f0' }}>
              <Text strong style={{ fontSize: 14 }}>Direct Messages</Text>
            </div>
            {dmRooms.map((room) => (
              <div
                key={room.id}
                onClick={() => onSelectRoom(room)}
                style={{
                  padding: '10px 16px',
                  cursor: 'pointer',
                  background: room.id === selectedRoomId ? '#e6f4ff' : 'transparent',
                  borderBottom: '1px solid #f5f5f5',
                }}
              >
                <Text><UserOutlined style={{ marginRight: 6 }} />{room.name.replace(/^dm-\d+-\d+$/, 'Direct Message')}</Text>
              </div>
            ))}
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
