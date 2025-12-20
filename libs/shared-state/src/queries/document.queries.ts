import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useApiClient } from '../api/ApiClientContext';

/**
 * Query key factory for document-related queries
 */
export const documentKeys = {
  all: ['documents'] as const,
  list: (memberEid: string) => [...documentKeys.all, 'list', memberEid] as const,
  detail: (memberEid: string, documentId: string) =>
    [...documentKeys.all, 'detail', memberEid, documentId] as const,
};

/**
 * Document type enum matching backend
 */
export type DocumentType =
  | 'IDENTIFICATION'
  | 'MEDICAL'
  | 'LEGAL'
  | 'CORRESPONDENCE'
  | 'EDUCATION'
  | 'OTHER';

/**
 * Document data from API
 */
export interface DocumentData {
  id: string;
  memberEid: string;
  fileName: string;
  contentType: string;
  fileSize: number;
  description?: string;
  documentType: DocumentType;
  uploadedBy: string;
  uploadedByPersona: string;
  createdAt: string;
  updatedAt: string;
}

/**
 * Document upload request
 */
export interface DocumentUploadRequest {
  file: File;
  description?: string;
  documentType?: DocumentType;
}

/**
 * Hook to list all documents for a member (youth)
 */
export const useDocuments = (memberEid: string, enabled = true) => {
  const api = useApiClient();

  return useQuery({
    queryKey: documentKeys.list(memberEid),
    queryFn: () => api.get<DocumentData[]>(`/api/member/${memberEid}/documents`),
    enabled: !!memberEid && enabled,
  });
};

/**
 * Hook to get a single document's metadata
 */
export const useDocument = (memberEid: string, documentId: string, enabled = true) => {
  const api = useApiClient();

  return useQuery({
    queryKey: documentKeys.detail(memberEid, documentId),
    queryFn: () =>
      api.get<DocumentData>(`/api/member/${memberEid}/documents/${documentId}`),
    enabled: !!memberEid && !!documentId && enabled,
  });
};

/**
 * Hook to upload a document for a member (youth)
 */
export const useUploadDocument = (memberEid: string) => {
  const api = useApiClient();
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ file, description, documentType }: DocumentUploadRequest) =>
      api.uploadFile<DocumentData>(`/api/member/${memberEid}/documents`, file, {
        ...(description && { description }),
        ...(documentType && { documentType }),
      }),
    onSuccess: () => {
      // Invalidate document list to refetch
      queryClient.invalidateQueries({ queryKey: documentKeys.list(memberEid) });
    },
  });
};

/**
 * Hook to delete a document
 */
export const useDeleteDocument = (memberEid: string) => {
  const api = useApiClient();
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (documentId: string) =>
      api.delete(`/api/member/${memberEid}/documents/${documentId}`),
    onSuccess: () => {
      // Invalidate document list to refetch
      queryClient.invalidateQueries({ queryKey: documentKeys.list(memberEid) });
    },
  });
};

/**
 * Get download URL for a document
 */
export const getDocumentDownloadUrl = (memberEid: string, documentId: string): string =>
  `/api/member/${memberEid}/documents/${documentId}/download`;

/**
 * Format file size for display
 */
export const formatFileSize = (bytes: number): string => {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
};
