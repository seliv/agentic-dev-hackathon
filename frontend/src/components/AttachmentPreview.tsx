import { FileOutlined, DownloadOutlined } from '@ant-design/icons';
import type { AttachmentInfo } from '../api/types.ts';
import { attachmentApi } from '../api/attachments.ts';

interface Props {
  attachment: AttachmentInfo;
  onImageClick?: () => void;
}

function formatFileSize(bytes: number): string {
  if (bytes < 1024) return bytes + ' B';
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
}

export const AttachmentPreview = ({ attachment, onImageClick }: Props) => {
  const isImage = attachment.contentType.startsWith('image/');

  if (isImage && attachment.thumbnailUrl) {
    return (
      <div
        onClick={onImageClick}
        style={{
          cursor: 'pointer',
          marginTop: 4,
          borderRadius: 8,
          overflow: 'hidden',
          display: 'inline-block',
        }}
      >
        <img
          src={attachmentApi.getThumbnailUrl(attachment.id)}
          alt={attachment.originalFileName}
          style={{ maxWidth: 200, maxHeight: 200, display: 'block' }}
        />
      </div>
    );
  }

  return (
    <a
      href={attachmentApi.getDownloadUrl(attachment.id)}
      target="_blank"
      rel="noopener noreferrer"
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: 6,
        padding: '6px 10px',
        marginTop: 4,
        borderRadius: 6,
        background: 'rgba(0,0,0,0.04)',
        color: '#1677ff',
        textDecoration: 'none',
        fontSize: 13,
      }}
    >
      <FileOutlined />
      <span style={{ maxWidth: 150, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
        {attachment.originalFileName}
      </span>
      <span style={{ color: 'rgba(0,0,0,0.4)', fontSize: 11 }}>
        {formatFileSize(attachment.fileSize)}
      </span>
      <DownloadOutlined />
    </a>
  );
};
