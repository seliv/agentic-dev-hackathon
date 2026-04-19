import { Button, List, Typography } from 'antd';
import { CheckOutlined, CloseOutlined } from '@ant-design/icons';
import type { RoomInvitation } from '../api/types.ts';

const { Text } = Typography;

interface Props {
  invitations: RoomInvitation[];
  onAccept: (id: number) => void;
  onDecline: (id: number) => void;
}

export const InvitationList = ({ invitations, onAccept, onDecline }: Props) => {
  if (invitations.length === 0) {
    return <Text type="secondary">No pending invitations</Text>;
  }

  return (
    <List
      dataSource={invitations}
      renderItem={(inv) => (
        <List.Item
          actions={[
            <Button size="small" type="primary" icon={<CheckOutlined />} onClick={() => onAccept(inv.id)}>Accept</Button>,
            <Button size="small" icon={<CloseOutlined />} onClick={() => onDecline(inv.id)}>Decline</Button>,
          ]}
        >
          <List.Item.Meta
            title={`#${inv.roomName}`}
            description={`Invited by ${inv.inviterUsername}`}
          />
        </List.Item>
      )}
    />
  );
};
