import { useState } from 'react';
import { Modal, Form, Input, Radio, message } from 'antd';
import { roomsApi } from '../api/rooms.ts';
import type { ChatRoom } from '../api/types.ts';

interface Props {
  open: boolean;
  onClose: () => void;
  onCreated: (room: ChatRoom) => void;
}

export const CreateRoomModal = ({ open, onClose, onCreated }: Props) => {
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);

  const handleOk = async () => {
    const values = await form.validateFields();
    setLoading(true);
    try {
      const room = await roomsApi.createRoom(values);
      message.success('Room created');
      form.resetFields();
      onCreated(room);
      onClose();
    } catch (error: unknown) {
      const err = error as { response?: { data?: { message?: string } } };
      message.error(err.response?.data?.message || 'Failed to create room');
    } finally {
      setLoading(false);
    }
  };

  return (
    <Modal title="Create Room" open={open} onOk={handleOk} onCancel={onClose} confirmLoading={loading}>
      <Form form={form} layout="vertical" initialValues={{ type: 'PUBLIC' }}>
        <Form.Item name="name" label="Room Name" rules={[{ required: true, message: 'Room name is required' }]}>
          <Input placeholder="e.g. general" />
        </Form.Item>
        <Form.Item name="description" label="Description">
          <Input.TextArea placeholder="What is this room about?" rows={3} />
        </Form.Item>
        <Form.Item name="type" label="Type">
          <Radio.Group>
            <Radio value="PUBLIC">Public</Radio>
            <Radio value="PRIVATE">Private (invite-only)</Radio>
          </Radio.Group>
        </Form.Item>
      </Form>
    </Modal>
  );
};
