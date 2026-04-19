import { useState, useEffect } from 'react';
import { Modal, List, Button, message } from 'antd';
import { friendsApi } from '../api/friends.ts';
import { invitationsApi } from '../api/invitations.ts';
import type { Friendship } from '../api/types.ts';

interface Props {
  open: boolean;
  roomId: string;
  onClose: () => void;
}

export const InviteToRoomModal = ({ open, roomId, onClose }: Props) => {
  const [friends, setFriends] = useState<Friendship[]>([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (open) {
      setLoading(true);
      friendsApi.getFriends()
        .then(setFriends)
        .catch(() => message.error('Failed to load friends'))
        .finally(() => setLoading(false));
    }
  }, [open]);

  const handleInvite = async (userId: number) => {
    try {
      await invitationsApi.inviteToRoom(roomId, userId);
      message.success('Invitation sent');
    } catch (error: unknown) {
      const err = error as { response?: { data?: { message?: string } } };
      message.error(err.response?.data?.message || 'Failed to invite');
    }
  };

  return (
    <Modal title="Invite to Room" open={open} onCancel={onClose} footer={null}>
      <List
        loading={loading}
        dataSource={friends}
        renderItem={(friend) => (
          <List.Item
            actions={[
              <Button size="small" type="primary" onClick={() => handleInvite(friend.friendUserId)}>Invite</Button>,
            ]}
          >
            <List.Item.Meta
              title={friend.friendUsername}
              description={friend.friendDisplayName}
            />
          </List.Item>
        )}
        locale={{ emptyText: 'No friends to invite' }}
      />
    </Modal>
  );
};
