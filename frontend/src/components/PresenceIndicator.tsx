import type { PresenceStatus } from '../api/types.ts';

interface Props {
  status: PresenceStatus;
  size?: number;
}

const statusColors: Record<PresenceStatus, string> = {
  ONLINE: '#52c41a',
  AFK: '#faad14',
  OFFLINE: '#d9d9d9',
};

export const PresenceIndicator = ({ status, size = 8 }: Props) => (
  <span
    title={status.toLowerCase()}
    style={{
      display: 'inline-block',
      width: size,
      height: size,
      borderRadius: '50%',
      backgroundColor: statusColors[status],
      flexShrink: 0,
    }}
  />
);
