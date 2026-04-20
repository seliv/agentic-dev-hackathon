const API_BASE = import.meta.env.VITE_API_URL || 'http://localhost:8080/api';

export const attachmentApi = {
  getDownloadUrl: (attachmentId: string): string => {
    return `${API_BASE}/attachments/${attachmentId}`;
  },

  getThumbnailUrl: (attachmentId: string): string => {
    return `${API_BASE}/attachments/${attachmentId}/thumbnail`;
  },
};
