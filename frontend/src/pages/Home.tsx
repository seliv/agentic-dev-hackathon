import { Typography } from 'antd';
import { useAuth } from '../contexts/AuthContext.tsx';
import { AppHeader } from '../components/AppHeader.tsx';

const { Title } = Typography;

export const Home = () => {
  const { user } = useAuth();

  return (
    <div style={{ display: 'flex', flexDirection: 'column', minHeight: '100vh' }}>
      <AppHeader />
      <div style={{ flex: 1, display: 'flex', justifyContent: 'center', alignItems: 'center', backgroundColor: '#f0f2f5' }}>
        <Title>Hello {user?.displayName || user?.username}</Title>
      </div>
    </div>
  );
};
