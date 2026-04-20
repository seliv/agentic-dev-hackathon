import { Modal, Button } from 'antd';
import { DownloadOutlined } from '@ant-design/icons';
import type { AttachmentInfo } from '../api/types.ts';
import { attachmentApi } from '../api/attachments.ts';

interface Props {
  attachment: AttachmentInfo | null;
  onClose: () => void;
}

export const ImageViewer = ({ attachment, onClose }: Props) => {
  if (!attachment) return null;

  return (
    <Modal
      open={!!attachment}
      onCancel={onClose}
      footer={[
        <Button
          key="download"
          icon={<DownloadOutlined />}
          href={attachmentApi.getDownloadUrl(attachment.id)}
          target="_blank"
        >
          Download
        </Button>,
      ]}
      width="80vw"
      centered
    >
      <img
        src={attachmentApi.getDownloadUrl(attachment.id)}
        alt={attachment.originalFileName}
        style={{ width: '100%', maxHeight: '70vh', objectFit: 'contain' }}
      />
    </Modal>
  );
};
