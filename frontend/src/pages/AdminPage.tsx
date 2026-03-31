import { useState, useMemo } from 'react';
import { Box, Typography, Tabs, Tab } from '@mui/material';
import AdminPanelSettingsIcon from '@mui/icons-material/AdminPanelSettings';
import { useQuery } from '@tanstack/react-query';
import { getErrors } from '../api/errors';
import ErrorRecordTable from '../components/admin/ErrorRecordTable';

interface TabPanelProps {
  children?: React.ReactNode;
  index: number;
  value: number;
}

function TabPanel({ children, index, value }: TabPanelProps) {
  return value === index ? <Box pt={2}>{children}</Box> : null;
}

export default function AdminPage() {
  const [tab, setTab] = useState(0);
  const [page, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useState(25);
  const [statusFilter, setStatusFilter] = useState('');
  const [moduleFilter, setModuleFilter] = useState('');

  const { data: errorPage, isLoading } = useQuery({
    queryKey: ['error-records', page, rowsPerPage, statusFilter, moduleFilter],
    queryFn: () =>
      getErrors({
        page,
        size: rowsPerPage,
        status: statusFilter || undefined,
        module: moduleFilter || undefined,
      }),
    staleTime: 30_000,
  });

  const modules = useMemo(
    () => Array.from(new Set(errorPage?.content.map((r) => r.sourceModule) ?? [])),
    [errorPage]
  );

  return (
    <Box>
      <Box display="flex" alignItems="center" gap={1} mb={2}>
        <AdminPanelSettingsIcon color="primary" />
        <Typography variant="h5">Administration</Typography>
      </Box>

      <Tabs value={tab} onChange={(_, v) => setTab(v)} sx={{ borderBottom: 1, borderColor: 'divider' }}>
        <Tab label="Data Quality / Errors" />
      </Tabs>

      <TabPanel value={tab} index={0}>
        <ErrorRecordTable
          records={errorPage?.content ?? []}
          total={errorPage?.totalElements ?? 0}
          loading={isLoading}
          page={page}
          rowsPerPage={rowsPerPage}
          onPageChange={(p) => setPage(p)}
          onRowsPerPageChange={(r) => { setRowsPerPage(r); setPage(0); }}
          statusFilter={statusFilter}
          onStatusFilterChange={(s) => { setStatusFilter(s); setPage(0); }}
          moduleFilter={moduleFilter}
          onModuleFilterChange={(m) => { setModuleFilter(m); setPage(0); }}
          modules={modules}
        />
      </TabPanel>
    </Box>
  );
}
