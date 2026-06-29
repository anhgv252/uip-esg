export type DraftStatus = 'PENDING_REVIEW' | 'APPROVED' | 'REJECTED' | 'SIMULATED' | 'EXECUTED';

export interface WorkflowDraft {
  id: string;
  tenantId: string;
  intent: string;
  bpmnXml: string;
  confidence: number;
  status: DraftStatus;
  requestedBy: string;
  approvedBy?: string;
  rejectionReason?: string;
  extractedEntities: Record<string, string>;
  version: number;
  nlParseLatencyMs: number;
  createdAt: string;
  updatedAt: string;
}

export interface SimulationStep {
  elementId: string;
  elementType: string;
  elementName: string;
  status: 'OK' | 'WARNING' | 'SKIPPED' | 'ERROR';
  message: string;
}

export interface SimulationResult {
  draftId: string;
  intent: string;
  success: boolean;
  steps: SimulationStep[];
  warnings: string[];
  durationMs: number;
  simulatedAt: string;
}

export interface BuildingRoiResponse {
  buildingId: string;
  buildingName: string;
  month: string;
  costs: {
    baseFeeVnd: number;
    aiTokensUsed: number;
    aiOverageTokens: number;
    aiOverageCostVnd: number;
    totalCostVnd: number;
    sensorReadings: number;
    alertsGenerated: number;
  };
  savings: {
    manualOpsCostVnd: number;
    automationSavingsVnd: number;
    paybackMonths: number;
    co2SavedKg: number;
  };
  comparisonChart: Array<{
    metric: string;
    before: number;
    after: number;
    unit: string;
  }>;
}
