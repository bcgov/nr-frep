import {
  Column,
  DataTable,
  Grid,
  InlineNotification,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableHeader,
  TableRow,
  Tag,
} from '@carbon/react';
import { useMemo, type FC } from 'react';

import TableHeaderBar from '@/components/core/TableHeaderBar';

import { FREP_REPORT_DEFINITIONS } from './reportDefinitions';

import { useAuthorization } from '@/hooks/useAuthorization';

import './reports.scss';

const TABLE_HEADERS = [
  { key: 'id', header: 'Report ID' },
  { key: 'title', header: 'Report' },
  { key: 'category', header: 'Category' },
  { key: 'description', header: 'Description' },
  { key: 'access', header: 'Access' },
] as const;

const ReportsPage: FC = () => {
  const { isSysAdmin } = useAuthorization();

  const visibleReports = useMemo(
    () => FREP_REPORT_DEFINITIONS.filter((report) => !report.adminOnly || isSysAdmin),
    [isSysAdmin],
  );

  const tableRows = useMemo(
    () =>
      visibleReports.map((report) => ({
        id: report.id,
        title: report.title,
        category: report.category,
        description: report.description,
        access: report.adminOnly ? 'Admin only' : 'All roles',
      })),
    [visibleReports],
  );

  return (
    <Grid fullWidth className="default-grid reports-grid">
      <Column sm={4} md={8} lg={16}>
        <h1 className="reports__title">Reports</h1>
        <p className="reports__subtitle">
          Catalog of legacy FREP Jasper reports. Generation is not yet available in this application
          — browse the list below.
        </p>
      </Column>

      <Column sm={4} md={8} lg={16}>
        <InlineNotification
          kind="info"
          title="Read-only catalog"
          subtitle="Report download and parameter forms will be added in a later phase. Legacy Jasper reports remain on JCRS until then."
          hideCloseButton
          lowContrast
        />
      </Column>

      <Column sm={4} md={8} lg={16}>
        <DataTable rows={tableRows} headers={[...TABLE_HEADERS]}>
          {({ rows, headers, getHeaderProps, getRowProps, getTableProps }) => (
            <TableContainer>
              <TableHeaderBar title="Reports catalog" />
              <Table {...getTableProps()} aria-label="FREP reports catalog">
                <TableHead>
                  <TableRow>
                    {headers.map((header) => (
                      <TableHeader {...getHeaderProps({ header })} key={header.key}>
                        {header.header}
                      </TableHeader>
                    ))}
                  </TableRow>
                </TableHead>
                <TableBody>
                  {rows.map((row) => (
                    <TableRow {...getRowProps({ row })} key={row.id}>
                      {row.cells.map((cell) => (
                        <TableCell key={cell.id}>
                          {cell.info.header === 'access' ? (
                            <Tag type={cell.value === 'Admin only' ? 'purple' : 'blue'} size="sm">
                              {cell.value}
                            </Tag>
                          ) : (
                            cell.value
                          )}
                        </TableCell>
                      ))}
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          )}
        </DataTable>
      </Column>
    </Grid>
  );
};

export default ReportsPage;
