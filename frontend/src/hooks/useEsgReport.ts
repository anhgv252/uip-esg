import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { triggerReportGeneration, getReportStatus, downloadReport } from '../api/esg';

export function useEsgReportGenerate() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ year, quarter }: { year: number; quarter: number }) =>
      triggerReportGeneration(year, quarter),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['esg-report-status'] });
    },
  });
}

export function useEsgReportStatus(reportId: string | null) {
  return useQuery({
    queryKey: ['esg-report-status', reportId],
    queryFn: () => getReportStatus(reportId!),
    enabled: !!reportId,
    refetchInterval: (query) => {
      const status = query.state.data?.status;
      return status === 'GENERATING' || status === 'PENDING' ? 3000 : false;
    },
    staleTime: 0,
  });
}

export function useEsgReportDownload() {
  return useMutation({
    mutationFn: ({ reportId, format = 'xlsx' }: { reportId: string; format?: string }) => {
      return downloadReport(reportId, format).then((blob) => {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `esg-report-${reportId}.${format}`;
        a.click();
        URL.revokeObjectURL(url);
      });
    },
  });
}
