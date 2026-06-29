import { useQuery } from '@tanstack/react-query';
import { apiClient } from '@/api/client';
import type { Iso37120Report, Iso37120Indicator } from '@/types/iso37120';

export function useIso37120Report(year?: number) {
  const currentYear = year || new Date().getFullYear();

  return useQuery({
    queryKey: ['iso37120-report', currentYear],
    queryFn: async () => {
      await new Promise((resolve) => setTimeout(resolve, 700));

      // Mock data - 9 key indicators from ISO 37120
      const mockIndicators: Iso37120Indicator[] = [
        {
          code: '5.1',
          name: 'Electricity consumption per capita',
          value: 2450,
          unit: 'kWh/year',
          dataAvailable: true,
          dataSource: 'EVN Metering',
          category: 'ENERGY',
          lastUpdated: new Date().toISOString(),
        },
        {
          code: '5.2',
          name: 'Renewable energy share',
          value: 18.5,
          unit: '%',
          dataAvailable: true,
          dataSource: 'Solar Panel Registry',
          category: 'ENERGY',
          lastUpdated: new Date().toISOString(),
        },
        {
          code: '8.1',
          name: 'Ambient air quality (PM2.5)',
          value: 32,
          unit: 'μg/m³',
          dataAvailable: true,
          dataSource: 'Air Quality Sensor Network',
          category: 'ENVIRONMENT',
          lastUpdated: new Date().toISOString(),
        },
        {
          code: '8.2',
          name: 'GHG emissions per capita',
          value: 4.2,
          unit: 'tonnes CO₂e/year',
          dataAvailable: true,
          dataSource: 'City Emission Inventory',
          category: 'ENVIRONMENT',
          lastUpdated: new Date().toISOString(),
        },
        {
          code: '16.1',
          name: 'Green space per capita',
          value: 12.5,
          unit: 'm²',
          dataAvailable: true,
          dataSource: 'GIS Analysis',
          category: 'SHELTER',
          lastUpdated: new Date().toISOString(),
        },
        {
          code: '18.1',
          name: 'Annual water consumption',
          value: 145,
          unit: 'L/capita/day',
          dataAvailable: true,
          dataSource: 'Water Utility',
          category: 'ENVIRONMENT',
          lastUpdated: new Date().toISOString(),
        },
        {
          code: '19.1',
          name: 'Solid waste collected',
          value: 0.85,
          unit: 'kg/capita/day',
          dataAvailable: true,
          dataSource: 'Waste Management',
          category: 'ENVIRONMENT',
          lastUpdated: new Date().toISOString(),
        },
        {
          code: '12.1',
          name: 'Public transport trips',
          value: undefined,
          unit: 'trips/capita/year',
          dataAvailable: false,
          dataSource: 'Manual Survey',
          category: 'GOVERNANCE',
        },
        {
          code: '14.1',
          name: 'Number of firefighters per 100,000',
          value: undefined,
          unit: 'per 100k',
          dataAvailable: false,
          dataSource: 'Fire Department',
          category: 'SAFETY',
        },
      ];

      const report: Iso37120Report = {
        year: currentYear,
        indicators: mockIndicators,
        totalIndicators: mockIndicators.length,
        calculatedIndicators: mockIndicators.filter((ind) => ind.dataAvailable).length,
        lastUpdated: new Date().toISOString(),
      };

      return report;
    },
    staleTime: 10 * 60 * 1000,
  });
}
