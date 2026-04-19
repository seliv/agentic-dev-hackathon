import { Popover, Button, Space, Typography, message } from 'antd';
import { UserAddOutlined, MessageOutlined, StopOutlined } from '@ant-design/icons';
import { friendsApi } from '../api/friends.ts';
import { blocksApi } from '../api/blocks.ts';
import { directMessagesApi } from '../api/directMessages.ts';
import type { ChatRoom } from '../api/types.ts';

const { Text } = Typography;

interface Props {
  userId: number;
  username: string;
  displayName: string | null;
  currentUserId: number;
  children: React.ReactNode;
  onDMCreated?: (room: ChatRoom) => void;
}

export const UserProfilePopover = ({ userId, username, displayName, currentUserId, children, onDMCreated }: Props) => {
  if (userId === currentUserId) {
    return <>{children}</>;
  }

  const handleAddFriend = async () => {
    try {
      await friendsApi.sendRequest(userId);
      message.success('Friend request sent');
    } catch (error: unknown) {
      const err = error as { response?: { data?: { message?: string } } };
      message.error(err.response?.data?.message || 'Failed to send request');
    }
  };

  const handleBlock = async () => {
    try {
      await blocksApi.blockUser(userId);
      message.success('User blocked');
    } catch (error: unknown) {
      const err = error as { response?: { data?: { message?: string } } };
      message.error(err.response?.data?.message || 'Failed to block user');
    }
  };

  const handleDM = async () => {
    try {
      const room = await directMessagesApi.getOrCreateDMRoom(userId);
      onDMCreated?.(room);
    } catch (error: unknown) {
      const err = error as { response?: { data?: { message?: string } } };
      message.error(err.response?.data?.message || 'Failed to create DM');
    }
  };

  const content = (
    <div style={{ minWidth: 200 }}>
      <div style={{ marginBottom: 12 }}>
        <Text strong style={{ display: 'block' }}>{displayName || username}</Text>
        <Text type="secondary">@{username}</Text>
      </div>
      <Space direction="vertical" style={{ width: '100%' }}>
        <Button size="small" icon={<UserAddOutlined />} onClick={handleAddFriend} block>Add Friend</Button>
        <Button size="small" icon={<MessageOutlined />} onClick={handleDM} block>Send DM</Button>
        <Button size="small" icon={<StopOutlined />} onClick={handleBlock} danger block>Block</Button>
      </Space>
    </div>
  );

  return (
    <Popover content={content} trigger="click" placement="bottomLeft">
      <span style={{ cursor: 'pointer' }}>{children}</span>
    </Popover>
  );
};
