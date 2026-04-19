import { useState, useEffect } from 'react';
import { Drawer, Tabs, List, Button, Typography, message } from 'antd';
import { MessageOutlined, DeleteOutlined } from '@ant-design/icons';
import { friendsApi } from '../api/friends.ts';
import { blocksApi } from '../api/blocks.ts';
import { invitationsApi } from '../api/invitations.ts';
import { FriendRequestList } from './FriendRequestList.tsx';
import { InvitationList } from './InvitationList.tsx';
import type { Friendship, UserBlockInfo, RoomInvitation, ChatRoom } from '../api/types.ts';
import { directMessagesApi } from '../api/directMessages.ts';

const { Text } = Typography;

interface Props {
  open: boolean;
  onClose: () => void;
  onDMCreated: (room: ChatRoom) => void;
}

export const ContactsPanel = ({ open, onClose, onDMCreated }: Props) => {
  const [friends, setFriends] = useState<Friendship[]>([]);
  const [requests, setRequests] = useState<Friendship[]>([]);
  const [blocked, setBlocked] = useState<UserBlockInfo[]>([]);
  const [invitations, setInvitations] = useState<RoomInvitation[]>([]);
  const [loading, setLoading] = useState(false);

  const fetchData = async () => {
    setLoading(true);
    try {
      const [f, r, b, i] = await Promise.all([
        friendsApi.getFriends(),
        friendsApi.getPendingRequests(),
        blocksApi.getBlockedUsers(),
        invitationsApi.getMyInvitations(),
      ]);
      setFriends(f);
      setRequests(r);
      setBlocked(b);
      setInvitations(i);
    } catch {
      message.error('Failed to load contacts');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (open) fetchData();
  }, [open]);

  const handleAcceptFriend = async (id: number) => {
    await friendsApi.acceptRequest(id);
    message.success('Friend request accepted');
    fetchData();
  };

  const handleDeclineFriend = async (id: number) => {
    await friendsApi.declineRequest(id);
    message.success('Friend request declined');
    fetchData();
  };

  const handleRemoveFriend = async (id: number) => {
    await friendsApi.removeFriend(id);
    message.success('Friend removed');
    fetchData();
  };

  const handleUnblock = async (userId: number) => {
    await blocksApi.unblockUser(userId);
    message.success('User unblocked');
    fetchData();
  };

  const handleAcceptInvitation = async (id: number) => {
    await invitationsApi.acceptInvitation(id);
    message.success('Invitation accepted');
    fetchData();
  };

  const handleDeclineInvitation = async (id: number) => {
    await invitationsApi.declineInvitation(id);
    message.success('Invitation declined');
    fetchData();
  };

  const handleSendDM = async (userId: number) => {
    try {
      const room = await directMessagesApi.getOrCreateDMRoom(userId);
      onDMCreated(room);
      onClose();
    } catch {
      message.error('Failed to create DM');
    }
  };

  const items = [
    {
      key: 'friends',
      label: `Friends (${friends.length})`,
      children: (
        <List
          loading={loading}
          dataSource={friends}
          renderItem={(friend) => (
            <List.Item
              actions={[
                <Button size="small" icon={<MessageOutlined />} onClick={() => handleSendDM(friend.friendUserId)}>DM</Button>,
                <Button size="small" danger icon={<DeleteOutlined />} onClick={() => handleRemoveFriend(friend.id)}>Remove</Button>,
              ]}
            >
              <List.Item.Meta
                title={friend.friendUsername}
                description={friend.friendDisplayName}
              />
            </List.Item>
          )}
          locale={{ emptyText: 'No friends yet' }}
        />
      ),
    },
    {
      key: 'requests',
      label: `Requests (${requests.length})`,
      children: <FriendRequestList requests={requests} onAccept={handleAcceptFriend} onDecline={handleDeclineFriend} />,
    },
    {
      key: 'invitations',
      label: `Invitations (${invitations.length})`,
      children: <InvitationList invitations={invitations} onAccept={handleAcceptInvitation} onDecline={handleDeclineInvitation} />,
    },
    {
      key: 'blocked',
      label: `Blocked (${blocked.length})`,
      children: (
        <List
          loading={loading}
          dataSource={blocked}
          renderItem={(block) => (
            <List.Item
              actions={[
                <Button size="small" onClick={() => handleUnblock(block.blockedUserId)}>Unblock</Button>,
              ]}
            >
              <List.Item.Meta title={block.blockedUsername} />
            </List.Item>
          )}
          locale={{ emptyText: 'No blocked users' }}
        />
      ),
    },
  ];

  return (
    <Drawer title="Contacts" open={open} onClose={onClose} width={400}>
      <Tabs items={items} />
    </Drawer>
  );
};
