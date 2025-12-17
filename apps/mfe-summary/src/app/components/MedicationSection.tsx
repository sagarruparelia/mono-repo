import { useMedications } from '@mono-repo/shared-state';
import { DataSection, Column } from './DataSection';
import styles from './sections.module.css';

interface Medication {
  id: string;
  name: string;
  dosage: string;
  frequency: string;
  prescribedDate: string;
  prescriber?: string;
}

interface MedicationSectionProps {
  memberId: string;
}

const columns: Column<Medication>[] = [
  { key: 'name', header: 'Medication', className: styles.primaryCell },
  { key: 'dosage', header: 'Dosage', className: styles.monoCell },
  { key: 'frequency', header: 'Frequency' },
  { key: 'prescribedDate', header: 'Prescribed', render: (m) => new Date(m.prescribedDate).toLocaleDateString() },
  { key: 'prescriber', header: 'Prescriber', render: (m) => m.prescriber || '-' },
];

export function MedicationSection({ memberId }: MedicationSectionProps) {
  const { data, isLoading, error } = useMedications(memberId);

  return (
    <DataSection<Medication>
      data={data}
      isLoading={isLoading}
      error={error as Error | null}
      columns={columns}
      loadingMessage="Loading medication records..."
      emptyMessage="No medication records found"
      errorPrefix="Failed to load medications"
      getKey={(m) => m.id}
    />
  );
}
