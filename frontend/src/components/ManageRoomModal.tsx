import { useState, useEffect, useCallback } from 'react';
import { Modal, Tabs, List, Button, Input, Tag, Popconfirm, message, Space, Typography } from 'antd';
import { DeleteOutlined, StopOutlined, CrownOutlined } from '@ant-design/icons';
import type { ChatRoom, ChatRoomMember, RoomBan } from '../api/types.ts';
import { roomsApi } from '../api/rooms.ts';
import { moderationApi } from '../api/moderation.ts';

const { Text, Title } = Typography;
const { TextArea } = Input;

interface Props {
  open: boolean;
  room: ChatRoom;
  currentUserId: number;
  onClose: () => void;
  onRoomUpdated: (room: ChatRoom) => void;
  onRoomDeleted: () => void;
}

export const ManageRoomModal = ({ open, room, currentUserId, onClose, onRoomUpdated, onRoomDeleted }: Props) => {
  const [members, setMembers] = useState<ChatRoomMember[]>([]);
  const [bans, setBans] = useState<RoomBan[]>([]);
  const [editName, setEditName] = useState(room.name);
  const [editDescription, setEditDescription] = useState(room.description || '');
  const [loading, setLoading] = useState(false);

  const isOwner = room.ownerId === currentUserId;
  const currentMember = members.find(m => m.userId === currentUserId);
  const isAdmin = currentMember?.role === 'ADMIN' || isOwner;

  const loadMembers = useCallback(async () => {
    try {
      const data = await roomsApi.getMembers(room.id);
      setMembers(data);
    } catch (err) {
      console.error('Failed to load members', err);
    }
  }, [room.id]);

  const loadBans = useCallback(async () => {
    if (!isAdmin) return;
    try {
      const data = await moderationApi.getBannedUsers(room.id);
      setBans(data);
    } catch (err) {
      console.error('Failed to load bans', err);
    }
  }, [room.id, isAdmin]);

  useEffect(() => {
    if (open) {
      loadMembers();
      loadBans();
      setEditName(room.name);
      setEditDescription(room.description || '');
    }
  }, [open, loadMembers, loadBans, room.name, room.description]);

  const handleChangeRole = async (userId: number, newRole: string) => {
    try {
      await moderationApi.changeRole(room.id, userId, newRole);
      message.success(`Role updated to ${newRole}`);
      loadMembers();
    } catch (err: any) {
      message.error(err.response?.data?.message || 'Failed to change role');
    }
  };

  const handleBan = async (userId: number) => {
    try {
      await moderationApi.banUser(room.id, userId);
      message.success('User banned');
      loadMembers();
      loadBans();
    } catch (err: any) {
      message.error(err.response?.data?.message || 'Failed to ban user');
    }
  };

  const handleUnban = async (userId: number) => {
    try {
      await moderationApi.unbanUser(room.id, userId);
      message.success('User unbanned');
      loadBans();
    } catch (err: any) {
      message.error(err.response?.data?.message || 'Failed to unban user');
    }
  };

  const handleSaveSettings = async () => {
    setLoading(true);
    try {
      const updated = await roomsApi.updateRoom(room.id, editName, editDescription);
      message.success('Room settings updated');
      onRoomUpdated(updated);
    } catch (err: any) {
      message.error(err.response?.data?.message || 'Failed to update room');
    } finally {
      setLoading(false);
    }
  };

  const handleDeleteRoom = async () => {
    setLoading(true);
    try {
      await roomsApi.deleteRoom(room.id);
      message.success('Room deleted');
      onRoomDeleted();
      onClose();
    } catch (err: any) {
      message.error(err.response?.data?.message || 'Failed to delete room');
    } finally {
      setLoading(false);
    }
  };

  const canActOnMember = (member: ChatRoomMember): boolean => {
    if (member.userId === currentUserId) return false;
    if (member.role === 'OWNER') return false;
    if (isOwner) return true;
    if (currentMember?.role === 'ADMIN') return true;
    return false;
  };

  const getRoleTag = (role: string) => {
    if (role === 'OWNER') return <Tag color="gold">Owner</Tag>;
    if (role === 'ADMIN') return <Tag color="blue">Admin</Tag>;
    return <Tag>Member</Tag>;
  };

  const membersTab = (
    <List
      dataSource={members}
      renderItem={(member) => (
        <List.Item
          actions={canActOnMember(member) ? [
            isOwner && member.role === 'MEMBER' && (
              <Button size="small" icon={<CrownOutlined />} onClick={() => handleChangeRole(member.userId, 'ADMIN')}>
                Make Admin
              </Button>
            ),
            isOwner && member.role === 'ADMIN' && (
              <Button size="small" onClick={() => handleChangeRole(member.userId, 'MEMBER')}>
                Remove Admin
              </Button>
            ),
            <Popconfirm title="Ban this user from the room?" onConfirm={() => handleBan(member.userId)} okText="Ban" okType="danger">
              <Button size="small" danger icon={<StopOutlined />}>Ban</Button>
            </Popconfirm>,
          ].filter(Boolean) : []}
        >
          <List.Item.Meta
            title={<Space>{member.displayName || member.username} {getRoleTag(member.role)}</Space>}
            description={`@${member.username}`}
          />
        </List.Item>
      )}
    />
  );

  const bannedTab = (
    <List
      dataSource={bans}
      locale={{ emptyText: 'No banned users' }}
      renderItem={(ban) => (
        <List.Item
          actions={[
            <Button size="small" onClick={() => handleUnban(ban.userId)}>Unban</Button>
          ]}
        >
          <List.Item.Meta
            title={ban.displayName || ban.username}
            description={
              <Space direction="vertical" size={0}>
                <Text type="secondary">@{ban.username}</Text>
                <Text type="secondary">Banned by @{ban.bannedByUsername}</Text>
                {ban.reason && <Text type="secondary">Reason: {ban.reason}</Text>}
              </Space>
            }
          />
        </List.Item>
      )}
    />
  );

  const settingsTab = (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      <div>
        <Text strong>Room Name</Text>
        <Input value={editName} onChange={(e) => setEditName(e.target.value)} />
      </div>
      <div>
        <Text strong>Description</Text>
        <TextArea rows={3} value={editDescription} onChange={(e) => setEditDescription(e.target.value)} />
      </div>
      <Button type="primary" onClick={handleSaveSettings} loading={loading}>
        Save Changes
      </Button>
      {isOwner && (
        <div style={{ marginTop: 24, borderTop: '1px solid #f0f0f0', paddingTop: 16 }}>
          <Title level={5} type="danger">Danger Zone</Title>
          <Popconfirm
            title="Delete this room?"
            description="This will permanently delete the room and all its messages. This cannot be undone."
            onConfirm={handleDeleteRoom}
            okText="Delete"
            okType="danger"
          >
            <Button danger icon={<DeleteOutlined />} loading={loading}>
              Delete Room
            </Button>
          </Popconfirm>
        </div>
      )}
    </div>
  );

  const tabItems = [
    { key: 'members', label: `Members (${members.length})`, children: membersTab },
    ...(isAdmin ? [{ key: 'banned', label: `Banned (${bans.length})`, children: bannedTab }] : []),
    ...(isAdmin ? [{ key: 'settings', label: 'Settings', children: settingsTab }] : []),
  ];

  return (
    <Modal
      title={`Manage: ${room.name}`}
      open={open}
      onCancel={onClose}
      footer={null}
      width={600}
    >
      <Tabs items={tabItems} />
    </Modal>
  );
};
