import { Layout, Dropdown, Button, Typography } from 'antd';
import { UserOutlined, SettingOutlined, LogoutOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext.tsx';
import type { MenuProps } from 'antd';

const { Header } = Layout;
const { Text } = Typography;

export const AppHeader = () => {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  const handleLogout = async () => {
    await logout();
    navigate('/signin');
  };

  const menuItems: MenuProps['items'] = [
    {
      key: 'settings',
      icon: <SettingOutlined />,
      label: 'Settings',
      onClick: () => navigate('/settings'),
    },
    { type: 'divider' },
    {
      key: 'logout',
      icon: <LogoutOutlined />,
      label: 'Logout',
      onClick: handleLogout,
    },
  ];

  return (
    <Header style={{
      display: 'flex',
      justifyContent: 'space-between',
      alignItems: 'center',
      background: '#fff',
      padding: '0 24px',
      borderBottom: '1px solid #f0f0f0',
    }}>
      <Text strong style={{ fontSize: 18 }}>ChatApp</Text>
      <Dropdown menu={{ items: menuItems }} placement="bottomRight">
        <Button icon={<UserOutlined />}>
          {user?.displayName || user?.username}
        </Button>
      </Dropdown>
    </Header>
  );
};
