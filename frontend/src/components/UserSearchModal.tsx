import { useState, useEffect } from 'react';
import { Modal, Input, List, Button, Typography, message } from 'antd';
import { UserAddOutlined, MessageOutlined } from '@ant-design/icons';
import { usersApi } from '../api/users.ts';
import { friendsApi } from '../api/friends.ts';
import { directMessagesApi } from '../api/directMessages.ts';
import type { UserSearchResult, ChatRoom } from '../api/types.ts';

const { Text } = Typography;

interface Props {
  open: boolean;
  onClose: () => void;
  onDMCreated: (room: ChatRoom) => void;
}

export const UserSearchModal = ({ open, onClose, onDMCreated }: Props) => {
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<UserSearchResult[]>([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!open) {
      setQuery('');
      setResults([]);
      return;
    }
  }, [open]);

  useEffect(() => {
    if (query.length < 2) {
      setResults([]);
      return;
    }

    const timer = setTimeout(async () => {
      setLoading(true);
      try {
        const data = await usersApi.searchUsers(query);
        setResults(data);
      } catch {
        message.error('Search failed');
      } finally {
        setLoading(false);
      }
    }, 300);

    return () => clearTimeout(timer);
  }, [query]);

  const handleAddFriend = async (userId: number) => {
    try {
      await friendsApi.sendRequest(userId);
      message.success('Friend request sent');
    } catch (error: unknown) {
      const err = error as { response?: { data?: { message?: string } } };
      message.error(err.response?.data?.message || 'Failed to send request');
    }
  };

  const handleSendDM = async (userId: number) => {
    try {
      const room = await directMessagesApi.getOrCreateDMRoom(userId);
      onDMCreated(room);
      onClose();
    } catch (error: unknown) {
      const err = error as { response?: { data?: { message?: string } } };
      message.error(err.response?.data?.message || 'Failed to create DM');
    }
  };

  return (
    <Modal title="Search Users" open={open} onCancel={onClose} footer={null} width={500}>
      <Input.Search
        placeholder="Search by username or display name..."
        value={query}
        onChange={(e) => setQuery(e.target.value)}
        style={{ marginBottom: 16 }}
        allowClear
      />
      <List
        loading={loading}
        dataSource={results}
        renderItem={(user) => (
          <List.Item
            actions={[
              <Button size="small" icon={<UserAddOutlined />} onClick={() => handleAddFriend(user.id)}>Add Friend</Button>,
              <Button size="small" icon={<MessageOutlined />} onClick={() => handleSendDM(user.id)}>DM</Button>,
            ]}
          >
            <List.Item.Meta
              title={user.username}
              description={user.displayName}
            />
          </List.Item>
        )}
        locale={{ emptyText: query.length >= 2 ? 'No users found' : 'Type at least 2 characters' }}
      />
    </Modal>
  );
};
