import { Button, List, Typography, Tag } from 'antd';
import { CheckOutlined, CloseOutlined } from '@ant-design/icons';
import type { Friendship } from '../api/types.ts';

const { Text } = Typography;

interface Props {
  requests: Friendship[];
  onAccept: (id: number) => void;
  onDecline: (id: number) => void;
}

export const FriendRequestList = ({ requests, onAccept, onDecline }: Props) => {
  const incoming = requests.filter(r => r.direction === 'INCOMING');
  const outgoing = requests.filter(r => r.direction === 'OUTGOING');

  return (
    <div>
      {incoming.length > 0 && (
        <>
          <Text strong style={{ display: 'block', marginBottom: 8 }}>Incoming</Text>
          <List
            dataSource={incoming}
            renderItem={(req) => (
              <List.Item
                actions={[
                  <Button size="small" type="primary" icon={<CheckOutlined />} onClick={() => onAccept(req.id)}>Accept</Button>,
                  <Button size="small" icon={<CloseOutlined />} onClick={() => onDecline(req.id)}>Decline</Button>,
                ]}
              >
                <List.Item.Meta
                  title={req.friendUsername}
                  description={req.friendDisplayName}
                />
              </List.Item>
            )}
          />
        </>
      )}
      {outgoing.length > 0 && (
        <>
          <Text strong style={{ display: 'block', marginTop: 16, marginBottom: 8 }}>Outgoing</Text>
          <List
            dataSource={outgoing}
            renderItem={(req) => (
              <List.Item>
                <List.Item.Meta
                  title={req.friendUsername}
                  description={req.friendDisplayName}
                />
                <Tag>Pending</Tag>
              </List.Item>
            )}
          />
        </>
      )}
      {incoming.length === 0 && outgoing.length === 0 && (
        <Text type="secondary">No pending requests</Text>
      )}
    </div>
  );
};
