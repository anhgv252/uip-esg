import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '@/api/client';
import type { BuildingRoiResponse } from '@/types/nlWorkflow';
import type { InvoiceListResponse, InvoiceStatus, GenerateInvoiceRequest, Invoice } from '@/types/billing';

export interface BuildingUsage {
  buildingId: string;
  buildingName: string;
  totalAiTokens: number;
  totalNlQueries: number;
  totalAlertRules: number;
}

export interface TenantUsage {
  month: string;
  buildingCount: number;
  totalAiTokens: number;
  totalNlQueries: number;
  totalAlertRules: number;
  buildingBreakdown: BuildingUsage[];
}

export interface BillingEvent {
  timestamp: string;
  eventType: 'NL_QUERY' | 'WORKFLOW_GENERATED' | 'ALERT_EXECUTED';
  buildingId: string;
  tokensUsed: number;
  metadata: Record<string, unknown>;
}

// M5-2 T11: mock data; M5-3 T08: replace with useQuery('/api/v1/billing/usage')
const MOCK_USAGE: TenantUsage = {
  month: '2026-06',
  buildingCount: 3,
  totalAiTokens: 185_000,
  totalNlQueries: 128,
  totalAlertRules: 15,
  buildingBreakdown: [
    {
      buildingId: 'BLD-001',
      buildingName: 'Tower A',
      totalAiTokens: 92_000,
      totalNlQueries: 56,
      totalAlertRules: 8,
    },
    {
      buildingId: 'BLD-002',
      buildingName: 'Tower B',
      totalAiTokens: 68_000,
      totalNlQueries: 45,
      totalAlertRules: 5,
    },
    {
      buildingId: 'BLD-003',
      buildingName: 'Office Complex C',
      totalAiTokens: 25_000,
      totalNlQueries: 27,
      totalAlertRules: 2,
    },
  ],
};

export function useBillingUsage(month: string) {
  return useQuery({
    queryKey: ['billing', 'usage', month],
    queryFn: async () => {
      // M5-2: mock data
      await new Promise((resolve) => setTimeout(resolve, 500));
      return MOCK_USAGE;
    },
    staleTime: 5 * 60 * 1000,
  });
}

export function useBillingEvents(buildingId: string, fromDate: string, toDate: string) {
  return useQuery({
    queryKey: ['billing', 'events', buildingId, fromDate, toDate],
    queryFn: async () => {
      // M5-2: mock data
      await new Promise((resolve) => setTimeout(resolve, 500));
      const events: BillingEvent[] = Array.from({ length: 20 }, (_, i) => ({
        timestamp: new Date(Date.now() - i * 3_600_000).toISOString(),
        eventType: ['NL_QUERY', 'WORKFLOW_GENERATED', 'ALERT_EXECUTED'][i % 3] as BillingEvent['eventType'],
        buildingId,
        tokensUsed: Math.floor(Math.random() * 2000) + 500,
        metadata: { userId: `user${i % 3 + 1}` },
      }));
      return events;
    },
    enabled: Boolean(buildingId && fromDate && toDate),
    staleTime: 2 * 60 * 1000,
  });
}

export function useBuildingRoi(buildingId: string, month?: string) {
  return useQuery({
    queryKey: ['building-roi', buildingId, month],
    queryFn: async () => {
      const url = `/api/v1/roi/building/${buildingId}${month ? `?month=${month}` : ''}`;
      return await apiClient.get<BuildingRoiResponse>(url);
    },
    enabled: !!buildingId,
  });
}

// Invoice management hooks
export function useInvoices(page: number = 1, status?: InvoiceStatus) {
  return useQuery({
    queryKey: ['invoices', page, status],
    queryFn: async () => {
      await new Promise((resolve) => setTimeout(resolve, 500));
      
      // Mock data
      const mockInvoices: Invoice[] = Array.from({ length: 10 }, (_, i) => ({
        id: `inv-${page}-${i}`,
        invoiceNumber: `INV-2026-${String(page * 10 + i).padStart(4, '0')}`,
        tenantId: 'tenant-1',
        periodMonth: `2026-${String(((page + i) % 12) + 1).padStart(2, '0')}`,
        totalAmountVnd: (Math.random() * 50_000_000 + 10_000_000),
        status: ['GENERATED', 'SENT', 'PAID', 'DISPUTED'][i % 4] as InvoiceStatus,
        generatedAt: new Date(Date.now() - i * 24 * 3600_000).toISOString(),
        pdfUrl: i % 3 === 0 ? `/api/v1/billing/invoices/inv-${page}-${i}/pdf` : undefined,
      }));

      const filtered = status ? mockInvoices.filter((inv) => inv.status === status) : mockInvoices;

      return {
        invoices: filtered,
        page,
        totalPages: 5,
        totalCount: 50,
      } as InvoiceListResponse;
    },
    staleTime: 60_000,
  });
}

export function useGenerateInvoice() {
  const queryClient = useQueryClient();
  
  return useMutation({
    mutationFn: async (request: GenerateInvoiceRequest) => {
      await new Promise((resolve) => setTimeout(resolve, 800));
      // Mock API call
      return { success: true };
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['invoices'] });
    },
  });
}

export function useMarkInvoicePaid() {
  const queryClient = useQueryClient();
  
  return useMutation({
    mutationFn: async (invoiceId: string) => {
      await new Promise((resolve) => setTimeout(resolve, 500));
      // Mock API call
      return { success: true };
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['invoices'] });
    },
  });
}
