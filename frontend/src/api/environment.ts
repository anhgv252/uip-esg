import { apiClient } from './client';

export interface Sensor {
  id: number;
  sensorCode: string;
  name: string;
  district: string;
  lat: number;
  lng: number;
  type: string;
  status: 'ONLINE' | 'OFFLINE' | 'MAINTENANCE';
  lastSeenAt: string | null;
}

export interface SensorReading {
  sensorId: number;
  timestamp: string;
  pm25: number | null;
  pm10: number | null;
  no2: number | null;
  o3: number | null;
  so2: number | null;
  co: number | null;
  temperature: number | null;
  humidity: number | null;
  aqi: number | null;
}

export interface AqiResponse {
  sensorId: number;
  sensorName: string;
  district: string;
  aqi: number;
  category: string;
  color: string;
  dominantPollutant: string;
  pm25: number | null;
  pm10: number | null;
  no2: number | null;
  o3: number | null;
  calculatedAt: string;
}

export interface AqiHistory {
  sensorId: number;
  timestamp: string;
  aqi: number;
  category: string;
}

export const getSensors = () =>
  apiClient.get<Sensor[]>('/environment/sensors').then((r) => r.data);

export const getSensorReadings = (sensorId: number, from?: string, to?: string) =>
  apiClient
    .get<SensorReading[]>(`/environment/sensors/${sensorId}/readings`, {
      params: { from, to },
    })
    .then((r) => r.data);

export const getCurrentAqi = () =>
  apiClient.get<AqiResponse[]>('/environment/aqi/current').then((r) => r.data);

export const getAqiHistory = (sensorId: number, from?: string, to?: string) =>
  apiClient
    .get<AqiHistory[]>('/environment/aqi/history', {
      params: { sensorId, from, to },
    })
    .then((r) => r.data);
