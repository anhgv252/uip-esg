import { apiClient } from './client';

export interface EsgSummary {
  period: string;
  totalEnergyKwh: number;
  totalWaterM3: number;
  totalCarbonKg: number;
  energyTrend: number;
  waterTrend: number;
  carbonTrend: number;
}

export interface EsgMetricEntry {
  buildingId: string;
  metricType: string;
  timestamp: string;
  value: number;
  unit: string;
}

export interface EsgReport {
  id: string;
  periodType: string;
  quarter: number;
  year: number;
  status: 'PENDING' | 'GENERATING' | 'DONE' | 'FAILED';
  downloadUrl: string | null;
  createdAt: string;
  generatedAt: string | null;
}

export const getEsgSummary = (year?: number, quarter?: number) =>
  apiClient
    .get<EsgSummary>('/esg/summary', { params: { year, quarter } })
    .then((r) => r.data);

export const getEsgEnergy = (from?: string, to?: string) =>
  apiClient
    .get<EsgMetricEntry[]>('/esg/energy', { params: { from, to } })
    .then((r) => r.data);

export const getEsgCarbon = (from?: string, to?: string) =>
  apiClient
    .get<EsgMetricEntry[]>('/esg/carbon', { params: { from, to } })
    .then((r) => r.data);

export const triggerReportGeneration = (year: number, quarter: number, period = 'quarterly') =>
  apiClient
    .post<EsgReport>('/esg/reports/generate', null, { params: { year, quarter, period } })
    .then((r) => r.data);

export const getReportStatus = (id: string) =>
  apiClient.get<EsgReport>(`/esg/reports/${id}/status`).then((r) => r.data);

export const downloadReport = (id: string) =>
  apiClient
    .get(`/esg/reports/${id}/download`, { responseType: 'blob' })
    .then((r) => r.data as Blob);
