import { apiClient } from './client';

// Backend may serialize Instant as epoch-seconds float before Jackson config fix propagates.
// normalizeInstant handles both ISO-8601 string and epoch-seconds number.
function normalizeInstant(value: string | number | null | undefined): string {
  if (value == null) return new Date().toISOString();
  if (typeof value === 'string') return value;
  return new Date(value * 1000).toISOString();
}

interface EsgMetricApiEntry {
  sourceId: string;
  metricType: string;
  timestamp: string | number;
  value: number;
  unit: string;
  buildingId: string | null;
  districtCode: string | null;
}

export interface EsgSummary {
  period: string;
  year: number;
  quarter: number;
  totalEnergyKwh: number;
  totalWaterM3: number;
  totalCarbonTco2e: number;
  totalWasteTons: number | null;
  sampleCount: number | null;
  energyTrend: number | null;
  waterTrend: number | null;
  carbonTrend: number | null;
}

export interface EsgMetricEntry {
  buildingId: string | null;
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

function mapMetricEntry(raw: EsgMetricApiEntry): EsgMetricEntry {
  return {
    buildingId: raw.buildingId,
    metricType: raw.metricType,
    timestamp: normalizeInstant(raw.timestamp),
    value: raw.value,
    unit: raw.unit,
  };
}

export const getEsgEnergy = (from?: string, to?: string) =>
  apiClient
    .get<EsgMetricApiEntry[]>('/esg/energy', { params: { from, to } })
    .then((r) => r.data.map(mapMetricEntry));

export const getEsgCarbon = (from?: string, to?: string) =>
  apiClient
    .get<EsgMetricApiEntry[]>('/esg/carbon', { params: { from, to } })
    .then((r) => r.data.map(mapMetricEntry));

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
