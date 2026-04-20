import { Suspense, lazy } from 'react';
import { Popover, Button, Spin } from 'antd';
import { SmileOutlined } from '@ant-design/icons';

const Picker = lazy(() => import('@emoji-mart/react').then(mod => ({ default: mod.default })));

interface Props {
  onSelect: (emoji: string) => void;
}

export const EmojiPicker = ({ onSelect }: Props) => {
  const handleSelect = (emoji: { native: string }) => {
    onSelect(emoji.native);
  };

  return (
    <Popover
      trigger="click"
      placement="topRight"
      content={
        <Suspense fallback={<Spin size="small" />}>
          <Picker
            data={async () => (await import('@emoji-mart/data')).default}
            onEmojiSelect={handleSelect}
            theme="light"
            previewPosition="none"
            skinTonePosition="none"
          />
        </Suspense>
      }
    >
      <Button type="text" icon={<SmileOutlined />} />
    </Popover>
  );
};
