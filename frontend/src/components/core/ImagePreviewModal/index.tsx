import { Modal } from '@/components/Modal';

import type { FC } from 'react';

import './image-preview-modal.scss';

export type ImagePreviewModalProps = {
  /** Whether the modal is open. */
  open: boolean;
  /** Full-size image source (data URL or http URL). When undefined the modal renders empty. */
  src?: string;
  /** Accessible alt / heading text for the image. */
  alt?: string;
  /** Called when the user closes the modal (close button, ESC, or click-outside). */
  onClose: () => void;
};

/**
 * A passive modal that shows a single image at full size — used for click-to-enlarge previews of
 * attachment / photo thumbnails (mirrors the legacy ModalPicture.vue).
 */
const ImagePreviewModal: FC<ImagePreviewModalProps> = ({ open, src, alt, onClose }) => (
  <Modal
    open={open}
    passiveModal
    size="lg"
    modalHeading={alt || 'Photo'}
    aria-label={alt || 'Photo preview'}
    onRequestClose={onClose}
  >
    <div className="image-preview-modal">
      {src && <img className="image-preview-modal__image" src={src} alt={alt || 'Photo preview'} />}
    </div>
  </Modal>
);

export default ImagePreviewModal;
