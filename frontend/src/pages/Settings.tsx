import { useState, useEffect } from 'react';
import {
  Tabs, Form, Input, Button, Card, Typography, message, Modal, Table, Tag,
} from 'antd';
import {
  KeyOutlined, DeleteOutlined, ExclamationCircleOutlined,
} from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext.tsx';
import { usersApi } from '../api/users.ts';
import type { SessionInfo } from '../api/types.ts';

const { Title, Text } = Typography;

const ProfileTab = () => {
  const { user } = useAuth();

  return (
    <div style={{ maxWidth: 400 }}>
      <Form layout="vertical">
        <Form.Item label="Username">
          <Input value={user?.username} disabled />
        </Form.Item>
        <Form.Item label="Email">
          <Input value={user?.email} disabled />
        </Form.Item>
        <Form.Item label="Display Name">
          <Input value={user?.displayName || ''} disabled />
        </Form.Item>
      </Form>
      <Text type="secondary">Profile editing will be available in a future update.</Text>
    </div>
  );
};

const SecurityTab = () => {
  const [passwordForm] = Form.useForm();
  const [loading, setLoading] = useState(false);
  const [deleteLoading, setDeleteLoading] = useState(false);
  const { logout } = useAuth();
  const navigate = useNavigate();

  const handleChangePassword = async (values: { currentPassword: string; newPassword: string }) => {
    setLoading(true);
    try {
      await usersApi.changePassword(values);
      message.success('Password changed successfully');
      passwordForm.resetFields();
    } catch (error: unknown) {
      const err = error as { response?: { data?: { message?: string } } };
      message.error(err.response?.data?.message || 'Failed to change password');
    } finally {
      setLoading(false);
    }
  };

  const handleDeleteAccount = () => {
    let password = '';
    Modal.confirm({
      title: 'Delete Account',
      icon: <ExclamationCircleOutlined />,
      content: (
        <div>
          <p>This action cannot be undone. Enter your password to confirm:</p>
          <Input.Password
            onChange={(e) => { password = e.target.value; }}
            placeholder="Enter your password"
          />
        </div>
      ),
      okText: 'Delete Account',
      okType: 'danger',
      onOk: async () => {
        if (!password) {
          message.error('Password is required');
          throw new Error('Password required');
        }
        setDeleteLoading(true);
        try {
          await usersApi.deleteAccount({ password });
          message.success('Account deleted');
          await logout();
          navigate('/signin');
        } catch (error: unknown) {
          const err = error as { response?: { data?: { message?: string } } };
          message.error(err.response?.data?.message || 'Failed to delete account');
          throw error;
        } finally {
          setDeleteLoading(false);
        }
      },
    });
  };

  return (
    <div style={{ maxWidth: 400 }}>
      <Title level={5}>Change Password</Title>
      <Form form={passwordForm} layout="vertical" onFinish={handleChangePassword}>
        <Form.Item
          name="currentPassword"
          label="Current Password"
          rules={[{ required: true, message: 'Please enter your current password' }]}
        >
          <Input.Password prefix={<KeyOutlined />} />
        </Form.Item>
        <Form.Item
          name="newPassword"
          label="New Password"
          rules={[
            { required: true, message: 'Please enter a new password' },
            { min: 8, message: 'Password must be at least 8 characters' },
          ]}
        >
          <Input.Password prefix={<KeyOutlined />} />
        </Form.Item>
        <Form.Item
          name="confirmPassword"
          label="Confirm New Password"
          dependencies={['newPassword']}
          rules={[
            { required: true, message: 'Please confirm your new password' },
            ({ getFieldValue }) => ({
              validator(_, value) {
                if (!value || getFieldValue('newPassword') === value) {
                  return Promise.resolve();
                }
                return Promise.reject(new Error('Passwords do not match'));
              },
            }),
          ]}
        >
          <Input.Password prefix={<KeyOutlined />} />
        </Form.Item>
        <Form.Item>
          <Button type="primary" htmlType="submit" loading={loading}>
            Change Password
          </Button>
        </Form.Item>
      </Form>

      <div style={{ marginTop: 48, paddingTop: 24, borderTop: '1px solid #f0f0f0' }}>
        <Title level={5} type="danger">Danger Zone</Title>
        <Button
          danger
          icon={<DeleteOutlined />}
          onClick={handleDeleteAccount}
          loading={deleteLoading}
        >
          Delete Account
        </Button>
      </div>
    </div>
  );
};

const SessionsTab = () => {
  const [sessions, setSessions] = useState<SessionInfo[]>([]);
  const [loading, setLoading] = useState(true);

  const fetchSessions = async () => {
    setLoading(true);
    try {
      const data = await usersApi.getSessions();
      setSessions(data);
    } catch {
      message.error('Failed to load sessions');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchSessions();
  }, []);

  const handleTerminate = async (sessionId: string) => {
    try {
      await usersApi.terminateSession(sessionId);
      message.success('Session terminated');
      fetchSessions();
    } catch {
      message.error('Failed to terminate session');
    }
  };

  const columns = [
    {
      title: 'Session',
      dataIndex: 'sessionId',
      key: 'sessionId',
      render: (id: string, record: SessionInfo) => (
        <span>
          {id.substring(0, 8)}...
          {record.current && <Tag color="green" style={{ marginLeft: 8 }}>Current</Tag>}
        </span>
      ),
    },
    {
      title: 'Created',
      dataIndex: 'createdAt',
      key: 'createdAt',
      render: (date: string) => new Date(date).toLocaleString(),
    },
    {
      title: 'Last Active',
      dataIndex: 'lastAccessedAt',
      key: 'lastAccessedAt',
      render: (date: string) => new Date(date).toLocaleString(),
    },
    {
      title: 'Action',
      key: 'action',
      render: (_: unknown, record: SessionInfo) => (
        !record.current && (
          <Button
            size="small"
            danger
            onClick={() => handleTerminate(record.sessionId)}
          >
            Terminate
          </Button>
        )
      ),
    },
  ];

  return (
    <Table
      columns={columns}
      dataSource={sessions}
      rowKey="sessionId"
      loading={loading}
      pagination={false}
    />
  );
};

export const Settings = () => {
  const items = [
    { key: 'profile', label: 'Profile', children: <ProfileTab /> },
    { key: 'security', label: 'Security', children: <SecurityTab /> },
    { key: 'sessions', label: 'Sessions', children: <SessionsTab /> },
  ];

  return (
    <div style={{ padding: 24, maxWidth: 800, margin: '0 auto' }}>
      <Title level={3}>Settings</Title>
      <Card>
        <Tabs items={items} />
      </Card>
    </div>
  );
};
