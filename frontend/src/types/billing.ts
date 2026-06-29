export type InvoiceStatus = 'GENERATED' | 'SENT' | 'PAID' | 'DISPUTED';

export interface Invoice {
  id: string;
  invoiceNumber: string;
  tenantId: string;
  periodMonth: string; // YYYY-MM
  totalAmountVnd: number;
  status: InvoiceStatus;
  generatedAt: string;
  sentAt?: string;
  paidAt?: string;
  disputedAt?: string;
  pdfUrl?: string;
  notes?: string;
}

export interface InvoiceListResponse {
  invoices: Invoice[];
  page: number;
  totalPages: number;
  totalCount: number;
}

export interface GenerateInvoiceRequest {
  month: string; // YYYY-MM
  year?: number;
}
