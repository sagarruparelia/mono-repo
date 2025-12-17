import { useImmunizations } from '@mono-repo/shared-state';
import { DataSection, Column } from './DataSection';
import styles from './sections.module.css';

interface Immunization {
  id: string;
  name: string;
  date: string;
  provider?: string;
  lotNumber?: string;
}

interface ImmunizationSectionProps {
  memberId: string;
}

const columns: Column<Immunization>[] = [
  { key: 'name', header: 'Vaccine', className: styles.primaryCell },
  { key: 'date', header: 'Date', render: (imm) => new Date(imm.date).toLocaleDateString() },
  { key: 'provider', header: 'Provider', render: (imm) => imm.provider || '-' },
  { key: 'lotNumber', header: 'Lot Number', className: styles.monoCell, render: (imm) => imm.lotNumber || '-' },
];

export function ImmunizationSection({ memberId }: ImmunizationSectionProps) {
  const { data, isLoading, error } = useImmunizations(memberId);

  return (
    <DataSection<Immunization>
      data={data}
      isLoading={isLoading}
      error={error as Error | null}
      columns={columns}
      loadingMessage="Loading immunization records..."
      emptyMessage="No immunization records found"
      errorPrefix="Failed to load immunizations"
      getKey={(imm) => imm.id}
    />
  );
}
