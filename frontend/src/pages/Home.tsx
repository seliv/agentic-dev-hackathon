import { Button, Typography } from 'antd';
import { LogoutOutlined } from '@ant-design/icons';
import { useAuth } from '../contexts/AuthContext.tsx';
import { useNavigate } from 'react-router-dom';

const { Title } = Typography;

export const Home = () => {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  const handleLogout = async () => {
    await logout();
    navigate('/signin');
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', minHeight: '100vh', backgroundColor: '#f0f2f5' }}>
      <div style={{ display: 'flex', justifyContent: 'flex-end', padding: '16px 24px' }}>
        <Button icon={<LogoutOutlined />} onClick={handleLogout}>
          Logout
        </Button>
      </div>
      <div style={{ flex: 1, display: 'flex', justifyContent: 'center', alignItems: 'center' }}>
        <Title>Hello {user?.displayName || user?.email}</Title>
      </div>
    </div>
  );
};
