import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
// apiClient imported via hooks below
import type { LotusVnReport, LotusBuilding } from '@/types/lotusVn';

export function useLotusScore(buildingId: string, period?: string) {
  return useQuery({
    queryKey: ['lotus-score', buildingId, period],
    queryFn: async () => {
      await new Promise((resolve) => setTimeout(resolve, 600));

      // Mock data
      const mockReport: LotusVnReport = {
        buildingId,
        buildingName: `Building ${buildingId}`,
        period: period || new Date().toISOString().slice(0, 7),
        overallScore: 72,
        certificationLevel: 'SILVER',
        categories: [
          { code: 'EN', name: 'Energy', score: 18, maxScore: 20, percentageScore: 90 },
          { code: 'WA', name: 'Water', score: 12, maxScore: 20, percentageScore: 60 },
          { code: 'IEQ', name: 'Indoor Quality', score: 14, maxScore: 20, percentageScore: 70 },
          { code: 'MA', name: 'Materials', score: 8, maxScore: 20, percentageScore: 40 },
          { code: 'ST', name: 'Site & Transport', score: 10, maxScore: 20, percentageScore: 50 },
        ],
        indicators: [
          {
            code: 'EN-01',
            name: 'Energy consumption intensity',
            actualValue: 120,
            benchmark: 150,
            score: 4,
            dataSource: 'Smart Meter',
            unit: 'kWh/m²/year',
          },
          {
            code: 'WA-01',
            name: 'Water consumption intensity',
            actualValue: 2.5,
            benchmark: 3.0,
            score: 3,
            dataSource: 'Water Meter',
            unit: 'm³/m²/year',
          },
          {
            code: 'IEQ-01',
            name: 'Indoor air quality (CO₂)',
            actualValue: 650,
            benchmark: 800,
            score: 4,
            dataSource: 'Air Quality Sensor',
            unit: 'ppm',
          },
          {
            code: 'MA-01',
            name: 'Recycled material percentage',
            actualValue: '15%',
            benchmark: '20%',
            score: 2,
            dataSource: 'Manual Input',
          },
          {
            code: 'ST-01',
            name: 'Green space ratio',
            actualValue: '25%',
            benchmark: '30%',
            score: 3,
            dataSource: 'GIS Analysis',
          },
        ],
        lastUpdated: new Date().toISOString(),
      };

      return mockReport;
    },
    enabled: !!buildingId,
    staleTime: 5 * 60 * 1000,
  });
}

export function useLotusBuildings() {
  return useQuery({
    queryKey: ['lotus-buildings'],
    queryFn: async () => {
      await new Promise((resolve) => setTimeout(resolve, 300));

      const mockBuildings: LotusBuilding[] = [
        { id: 'bldg-001', name: 'Green Tower Alpha', tenantId: 'tenant-1' },
        { id: 'bldg-002', name: 'Eco Plaza Beta', tenantId: 'tenant-1' },
        { id: 'bldg-003', name: 'Smart Office Gamma', tenantId: 'tenant-1' },
      ];

      return mockBuildings;
    },
    staleTime: 10 * 60 * 1000,
  });
}

export function useRefreshLotusScore() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (_params: { buildingId: string; period?: string }) => {
      await new Promise((resolve) => setTimeout(resolve, 1200));
      // Mock API call to trigger recalculation
      return { success: true };
    },
    onSuccess: (_, variables) => {
      queryClient.invalidateQueries({ queryKey: ['lotus-score', variables.buildingId] });
    },
  });
}
