export interface PartnerFeatureFlags {
  showEsgDashboard: boolean
  showTrafficModule: boolean
  showCitizenPortal: boolean
  showAdvancedReporting: boolean
  maxSensorCount: number
}

export const PARTNER_FEATURES: Record<string, PartnerFeatureFlags> = {
  'energy-optimizer': {
    showEsgDashboard: true,
    showTrafficModule: false,
    showCitizenPortal: false,
    showAdvancedReporting: true,
    maxSensorCount: 5000,
  },
  'citizen-first': {
    showEsgDashboard: false,
    showTrafficModule: true,
    showCitizenPortal: true,
    showAdvancedReporting: false,
    maxSensorCount: 1000,
  },
  default: {
    showEsgDashboard: true,
    showTrafficModule: true,
    showCitizenPortal: true,
    showAdvancedReporting: true,
    maxSensorCount: 10000,
  },
}

export function getPartnerFeatures(partnerKey?: string): PartnerFeatureFlags {
  return PARTNER_FEATURES[partnerKey ?? 'default'] ?? PARTNER_FEATURES.default
}
