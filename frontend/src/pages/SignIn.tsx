import { useState } from 'react';
import { Form, Input, Button, Card, Typography, message } from 'antd';
import { LockOutlined, MailOutlined } from '@ant-design/icons';
import { useNavigate, Link } from 'react-router-dom';
import { authApi } from '../api/auth.ts';
import { useAuth } from '../contexts/AuthContext.tsx';

const { Title } = Typography;

export const SignIn = () => {
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();
  const { login } = useAuth();

  const onFinish = async (values: { email: string; password: string }) => {
    setLoading(true);
    try {
      const user = await authApi.signIn(values);
      message.success('Signed in successfully!');
      login(user);
      navigate('/');
    } catch (error: unknown) {
      const err = error as { response?: { data?: { message?: string } } };
      message.error(err.response?.data?.message || 'Sign in failed');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', minHeight: '100vh', backgroundColor: '#f0f2f5' }}>
      <div style={{ flex: 1, display: 'flex', justifyContent: 'center', alignItems: 'center' }}>
        <Card style={{ width: 400 }}>
          <Title level={2} style={{ textAlign: 'center' }}>Sign In</Title>
          <Form
            name="signin"
            onFinish={onFinish}
            autoComplete="off"
            layout="vertical"
          >
            <Form.Item
              name="email"
              rules={[
                { required: true, message: 'Please input your email!' },
                { type: 'email', message: 'Please enter a valid email!' }
              ]}
            >
              <Input prefix={<MailOutlined />} placeholder="Email" />
            </Form.Item>

            <Form.Item
              name="password"
              rules={[{ required: true, message: 'Please input your password!' }]}
            >
              <Input.Password prefix={<LockOutlined />} placeholder="Password" />
            </Form.Item>

            <Form.Item>
              <Button type="primary" htmlType="submit" loading={loading} block>
                Sign In
              </Button>
            </Form.Item>

            <div style={{ textAlign: 'center' }}>
              Don't have an account? <Link to="/signup">Sign Up</Link>
            </div>
          </Form>

          <div style={{ marginTop: 24, paddingTop: 24, borderTop: '1px solid #f0f0f0' }}>
            <div style={{ textAlign: 'center', marginBottom: 12, color: '#8c8c8c', fontSize: '14px' }}>
              Try our demo accounts
            </div>
            <div style={{ display: 'flex', gap: 8, justifyContent: 'center' }}>
              <Button
                size="small"
                onClick={() => onFinish({ email: 'alice@demo.com', password: 'password' })}
                disabled={loading}
              >
                Alice
              </Button>
              <Button
                size="small"
                onClick={() => onFinish({ email: 'bob@demo.com', password: 'password' })}
                disabled={loading}
              >
                Bob
              </Button>
              <Button
                size="small"
                onClick={() => onFinish({ email: 'carol@demo.com', password: 'password' })}
                disabled={loading}
              >
                Carol
              </Button>
            </div>
          </div>
        </Card>
      </div>
    </div>
  );
};
