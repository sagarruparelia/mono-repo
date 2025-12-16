import { useState, useCallback, type DragEvent, type ChangeEvent } from 'react';
import {
  useDocuments,
  useUploadDocument,
  useDeleteDocument,
  getDocumentDownloadUrl,
  formatFileSize,
  type DocumentData,
  type DocumentType,
  type Persona,
} from '@mono-repo/shared-state';
import styles from './DocumentsSection.module.css';

interface DocumentsSectionProps {
  memberId: string;
  persona: Persona;
  canUpload: boolean;
  canDelete: boolean;
}

const DOCUMENT_TYPES: { value: DocumentType; label: string }[] = [
  { value: 'OTHER', label: 'Other' },
  { value: 'IDENTIFICATION', label: 'Identification' },
  { value: 'MEDICAL', label: 'Medical' },
  { value: 'LEGAL', label: 'Legal' },
  { value: 'CORRESPONDENCE', label: 'Correspondence' },
  { value: 'EDUCATION', label: 'Education' },
];

export function DocumentsSection({
  memberId,
  persona,
  canUpload,
  canDelete,
}: DocumentsSectionProps) {
  const { data: documents, isLoading, error, refetch } = useDocuments(memberId);
  const uploadMutation = useUploadDocument(memberId);
  const deleteMutation = useDeleteDocument(memberId);

  const [dragActive, setDragActive] = useState(false);
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [description, setDescription] = useState('');
  const [documentType, setDocumentType] = useState<DocumentType>('OTHER');

  const handleDrag = useCallback((e: DragEvent<HTMLDivElement>) => {
    e.preventDefault();
    e.stopPropagation();
    if (e.type === 'dragenter' || e.type === 'dragover') {
      setDragActive(true);
    } else if (e.type === 'dragleave') {
      setDragActive(false);
    }
  }, []);

  const handleDrop = useCallback((e: DragEvent<HTMLDivElement>) => {
    e.preventDefault();
    e.stopPropagation();
    setDragActive(false);

    if (e.dataTransfer.files && e.dataTransfer.files[0]) {
      setSelectedFile(e.dataTransfer.files[0]);
    }
  }, []);

  const handleFileChange = (e: ChangeEvent<HTMLInputElement>) => {
    if (e.target.files && e.target.files[0]) {
      setSelectedFile(e.target.files[0]);
    }
  };

  const handleUpload = async () => {
    if (!selectedFile) return;

    try {
      await uploadMutation.mutateAsync({
        file: selectedFile,
        description: description || undefined,
        documentType,
      });
      // Reset form on success
      setSelectedFile(null);
      setDescription('');
      setDocumentType('OTHER');
    } catch (error) {
      console.error('Upload failed:', error);
    }
  };

  const handleDelete = async (documentId: string, fileName: string) => {
    if (window.confirm(`Are you sure you want to delete "${fileName}"?`)) {
      try {
        await deleteMutation.mutateAsync(documentId);
      } catch (error) {
        console.error('Delete failed:', error);
      }
    }
  };

  const handleClearFile = () => {
    setSelectedFile(null);
    setDescription('');
    setDocumentType('OTHER');
  };

  if (isLoading) {
    return (
      <div className={styles.container}>
        <h3 className={styles.title}>Documents</h3>
        <div className={styles.loading}>Loading documents...</div>
      </div>
    );
  }

  if (error) {
    return (
      <div className={styles.container}>
        <h3 className={styles.title}>Documents</h3>
        <div className={styles.error}>
          Failed to load documents.
          <button onClick={() => refetch()} className={styles.retryButton}>
            Retry
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className={styles.container}>
      <h3 className={styles.title}>Documents</h3>

      {canUpload && (
        <div className={styles.uploadSection}>
          <div
            className={`${styles.dropzone} ${dragActive ? styles.active : ''}`}
            onDragEnter={handleDrag}
            onDragLeave={handleDrag}
            onDragOver={handleDrag}
            onDrop={handleDrop}
          >
            {selectedFile ? (
              <div className={styles.selectedFile}>
                <span className={styles.fileName}>{selectedFile.name}</span>
                <span className={styles.fileSize}>
                  ({formatFileSize(selectedFile.size)})
                </span>
                <button
                  onClick={handleClearFile}
                  className={styles.clearButton}
                  type="button"
                >
                  Clear
                </button>
              </div>
            ) : (
              <>
                <p className={styles.dropzoneText}>
                  Drag and drop a file here, or
                </p>
                <label className={styles.fileInputLabel}>
                  <input
                    type="file"
                    onChange={handleFileChange}
                    accept=".pdf,.jpg,.jpeg,.png,.doc,.docx"
                    className={styles.fileInput}
                  />
                  Browse Files
                </label>
                <p className={styles.dropzoneHint}>
                  PDF, JPG, PNG, DOC, DOCX (max 10MB)
                </p>
              </>
            )}
          </div>

          {selectedFile && (
            <div className={styles.uploadForm}>
              <input
                type="text"
                placeholder="Description (optional)"
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                className={styles.input}
                maxLength={500}
              />
              <select
                value={documentType}
                onChange={(e) => setDocumentType(e.target.value as DocumentType)}
                className={styles.select}
              >
                {DOCUMENT_TYPES.map((type) => (
                  <option key={type.value} value={type.value}>
                    {type.label}
                  </option>
                ))}
              </select>
              <button
                onClick={handleUpload}
                disabled={uploadMutation.isPending}
                className={styles.uploadButton}
                type="button"
              >
                {uploadMutation.isPending ? 'Uploading...' : 'Upload'}
              </button>
            </div>
          )}

          {uploadMutation.isError && (
            <div className={styles.uploadError}>
              Upload failed: {(uploadMutation.error as Error).message}
            </div>
          )}
        </div>
      )}

      <div className={styles.documentList}>
        {!documents || documents.length === 0 ? (
          <p className={styles.empty}>No documents uploaded</p>
        ) : (
          documents.map((doc: DocumentData) => (
            <div key={doc.id} className={styles.documentItem}>
              <div className={styles.documentInfo}>
                <span className={styles.documentName}>{doc.fileName}</span>
                <span className={styles.documentMeta}>
                  {formatFileSize(doc.fileSize)} | {doc.documentType}
                </span>
                {doc.description && (
                  <span className={styles.documentDescription}>
                    {doc.description}
                  </span>
                )}
                <span className={styles.documentUploader}>
                  Uploaded by: {doc.uploadedByPersona}
                </span>
              </div>
              <div className={styles.documentActions}>
                <a
                  href={getDocumentDownloadUrl(memberId, doc.id)}
                  className={styles.downloadButton}
                  download
                >
                  Download
                </a>
                {canDelete && (
                  <button
                    onClick={() => handleDelete(doc.id, doc.fileName)}
                    disabled={deleteMutation.isPending}
                    className={styles.deleteButton}
                    type="button"
                  >
                    Delete
                  </button>
                )}
              </div>
            </div>
          ))
        )}
      </div>
    </div>
  );
}
