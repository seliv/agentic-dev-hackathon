import { useState } from 'react';
import { Modal, Form, Input, message } from 'antd';
import { roomsApi } from '../api/rooms.ts';
import type { ChatRoom } from '../api/types.ts';

interface CreateRoomModalProps {
  open: boolean;
  onClose: () => void;
  onCreated: (room: ChatRoom) => void;
}

export const CreateRoomModal = ({ open, onClose, onCreated }: CreateRoomModalProps) => {
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);

  const handleOk = async () => {
    try {
      const values = await form.validateFields();
      setLoading(true);
      const room = await roomsApi.createRoom(values);
      message.success(`Room #${room.name} created`);
      form.resetFields();
      onCreated(room);
      onClose();
    } catch (err: unknown) {
      if (err && typeof err === 'object' && 'errorFields' in err) return;
      message.error('Failed to create room');
    } finally {
      setLoading(false);
    }
  };

  const handleCancel = () => {
    form.resetFields();
    onClose();
  };

  return (
    <Modal
      title="Create Room"
      open={open}
      onOk={handleOk}
      onCancel={handleCancel}
      confirmLoading={loading}
      destroyOnClose
    >
      <Form form={form} layout="vertical">
        <Form.Item
          name="name"
          label="Room Name"
          rules={[{ required: true, message: 'Please enter a room name' }]}
        >
          <Input placeholder="e.g. general" />
        </Form.Item>
        <Form.Item name="description" label="Description">
          <Input.TextArea placeholder="What is this room about?" rows={3} />
        </Form.Item>
      </Form>
    </Modal>
  );
};
