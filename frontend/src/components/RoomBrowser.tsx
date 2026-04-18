import { useState, useEffect } from 'react';
import { Modal, Input, List, Button, Typography, message } from 'antd';
import { SearchOutlined, TeamOutlined } from '@ant-design/icons';
import { roomsApi } from '../api/rooms.ts';
import type { ChatRoom } from '../api/types.ts';

const { Text } = Typography;

interface RoomBrowserProps {
  open: boolean;
  onClose: () => void;
  onJoined: (room: ChatRoom) => void;
  joinedRoomIds: Set<string>;
}

export const RoomBrowser = ({ open, onClose, onJoined, joinedRoomIds }: RoomBrowserProps) => {
  const [rooms, setRooms] = useState<ChatRoom[]>([]);
  const [search, setSearch] = useState('');
  const [loading, setLoading] = useState(false);
  const [joiningId, setJoiningId] = useState<string | null>(null);

  useEffect(() => {
    if (!open) return;
    const fetchRooms = async () => {
      setLoading(true);
      try {
        const result = await roomsApi.getPublicRooms(search || undefined);
        setRooms(result.content);
      } catch {
        message.error('Failed to load rooms');
      } finally {
        setLoading(false);
      }
    };
    const timer = setTimeout(fetchRooms, search ? 300 : 0);
    return () => clearTimeout(timer);
  }, [open, search]);

  const handleJoin = async (room: ChatRoom) => {
    setJoiningId(room.id);
    try {
      await roomsApi.joinRoom(room.id);
      message.success(`Joined #${room.name}`);
      onJoined(room);
    } catch {
      message.error('Failed to join room');
    } finally {
      setJoiningId(null);
    }
  };

  const handleClose = () => {
    setSearch('');
    setRooms([]);
    onClose();
  };

  return (
    <Modal
      title="Browse Rooms"
      open={open}
      onCancel={handleClose}
      footer={null}
      width={520}
      destroyOnClose
    >
      <Input
        prefix={<SearchOutlined />}
        placeholder="Search rooms..."
        value={search}
        onChange={(e) => setSearch(e.target.value)}
        style={{ marginBottom: 16 }}
        allowClear
      />
      <List
        loading={loading}
        dataSource={rooms}
        locale={{ emptyText: 'No rooms found' }}
        renderItem={(room) => (
          <List.Item
            actions={[
              joinedRoomIds.has(room.id) ? (
                <Text key="joined" type="secondary">Joined</Text>
              ) : (
                <Button
                  key="join"
                  type="primary"
                  size="small"
                  loading={joiningId === room.id}
                  onClick={() => handleJoin(room)}
                >
                  Join
                </Button>
              ),
            ]}
          >
            <List.Item.Meta
              title={`#${room.name}`}
              description={
                <span>
                  {room.description && <span>{room.description} &middot; </span>}
                  <TeamOutlined /> {room.memberCount}
                </span>
              }
            />
          </List.Item>
        )}
      />
    </Modal>
  );
};
