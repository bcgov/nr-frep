import type { FC, ReactNode } from 'react';

import './index.scss';

export type PrintColumn = { key: string; header: string };
export type PrintRow = Record<string, ReactNode>;

interface PrintableTableProps {
  /** Heading shown above the printed table. */
  title: string;
  /** Optional context line (district, master-list year, summary, generated date…). */
  meta?: ReactNode;
  columns: PrintColumn[];
  rows: PrintRow[];
}

/**
 * Print-only full-list table: hidden on screen, rendered for the browser print dialog. Mirrors the
 * legacy FREP "Printer Version" — a stripped-down view of the entire list (no app chrome, no
 * pagination) that the user prints from the browser. The visible interactive page should be wrapped
 * in a {@code print-hidden} element so only this region prints (see the global {@code @media print}
 * rules in styles/index.scss).
 */
export const PrintableTable: FC<PrintableTableProps> = ({ title, meta, columns, rows }) => (
  <div className="printable-table">
    <div className="printable-table__header">
      <h2 className="printable-table__title">{title}</h2>
      {meta != null && <p className="printable-table__meta">{meta}</p>}
    </div>
    <table className="printable-table__table">
      <thead>
        <tr>
          {columns.map((column) => (
            <th key={column.key}>{column.header}</th>
          ))}
        </tr>
      </thead>
      <tbody>
        {rows.map((row, index) => (
          <tr key={(row.id as string) ?? index}>
            {columns.map((column) => (
              <td key={column.key}>{row[column.key] ?? ''}</td>
            ))}
          </tr>
        ))}
      </tbody>
    </table>
  </div>
);

export default PrintableTable;
