import { apiClient } from './client';

export interface ForecastPoint {
  timestamp: string;
  actualValue: number | null;
  predictedValue: number;
  confidenceUpper: number;
  confidenceLower: number;
  isAnomaly: boolean;
}

export interface ForecastResponse {
  tenantId: string;
  buildingId: string;
  model: string;
  isFallback: boolean;
  mape: number | null;
  points: ForecastPoint[];
  generatedAt: string;
}

export const getEnergyForecast = (buildingId: string, horizonDays = 30) =>
  apiClient
    .get<ForecastResponse>('/forecast/energy', { params: { buildingId, horizonDays } })
    .then((r) => r.data);
