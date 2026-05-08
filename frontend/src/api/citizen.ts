import { apiClient } from './client'

export interface BuildingDto {
  id: string
  name: string
  address: string
  district: string
}

export interface CitizenProfileDto {
  id: string
  username: string
  email: string
  phone: string | null
  fullName: string
  cccd: string | null
  role: string
  createdAt: string
  household: HouseholdDto | null
}

export interface HouseholdDto {
  id: string
  buildingId: string
  buildingName: string
  floor: string
  unitNumber: string
}

export interface CitizenRegistrationRequest {
  fullName: string
  email: string
  phone: string
  cccd: string
  password: string
}

export interface CitizenRegistrationResponse {
  profile: CitizenProfileDto
  accessToken: string
  refreshToken: string
  tokenType: string
  expiresIn: number
}

export interface HouseholdRequest {
  buildingId: string
  floor: string
  unitNumber: string
}

export interface MeterDto {
  id: string
  meterCode: string
  meterType: 'ELECTRICITY' | 'WATER'
  registeredAt: string
}

export interface InvoiceDto {
  id: string
  citizenId: string
  meterId: string | null
  billingMonth: number
  billingYear: number
  meterType: 'ELECTRICITY' | 'WATER'
  unitsConsumed: number | null
  unitPrice: number | null
  amount: number
  status: 'UNPAID' | 'PAID' | 'OVERDUE'
  issuedAt: string
  paidAt: string | null
}

export interface InvoicePage {
  content: InvoiceDto[]
  totalElements: number
  totalPages: number
  number: number
}

export interface MeterRequest {
  meterCode: string
  meterType: 'ELECTRICITY' | 'WATER'
}

export const getBuildings = (tenantId?: string) => {
  const headers = tenantId ? { 'X-Tenant-Override': tenantId } : {}
  return apiClient.get<BuildingDto[]>('/citizen/buildings', { headers }).then((r) => r.data)
}

export const registerCitizen = (data: CitizenRegistrationRequest, tenantId?: string) => {
  const headers = tenantId ? { 'X-Tenant-Override': tenantId } : {}
  return apiClient.post<CitizenRegistrationResponse>('/citizen/register', data, { headers }).then((r) => r.data)
}

export const linkHousehold = (data: HouseholdRequest, tenantId?: string) => {
  const headers = tenantId ? { 'X-Tenant-Override': tenantId } : {}
  return apiClient
    .post<CitizenProfileDto>('/citizen/profile/household', data, { headers })
    .then((r) => r.data)
}

export const getCitizenProfile = (tenantId?: string) => {
  const headers = tenantId ? { 'X-Tenant-Override': tenantId } : {}
  return apiClient.get<CitizenProfileDto>('/citizen/profile', { headers }).then((r) => r.data)
}

export const registerMeter = (data: MeterRequest, tenantId?: string) => {
  const headers = tenantId ? { 'X-Tenant-Override': tenantId } : {}
  return apiClient.post<MeterDto>('/citizen/meters', data, { headers }).then((r) => r.data)
}

export const getInvoices = (
  params?: { month?: number; year?: number; page?: number; size?: number },
  tenantId?: string,
) => {
  const headers = tenantId ? { 'X-Tenant-Override': tenantId } : {}
  // When both month and year are provided, use the by-month endpoint (BUG-S3-05-01)
  if (params?.month != null && params?.year != null) {
    return apiClient
      .get<InvoiceDto[]>('/citizen/invoices/by-month', { params: { month: params.month, year: params.year }, headers })
      .then((r): InvoicePage => ({
        content: r.data,
        totalElements: r.data.length,
        totalPages: 1,
        number: 0,
      }))
  }
  return apiClient.get<InvoicePage>('/citizen/invoices', { params, headers }).then((r) => r.data)
}

export const getInvoiceById = (id: string, tenantId?: string) => {
  const headers = tenantId ? { 'X-Tenant-Override': tenantId } : {}
  return apiClient.get<InvoiceDto>(`/citizen/invoices/${id}`, { headers }).then((r) => r.data)
}

export const getMeters = (tenantId?: string) => {
  const headers = tenantId ? { 'X-Tenant-Override': tenantId } : {}
  return apiClient.get<MeterDto[]>('/citizen/meters', { headers }).then((r) => r.data)
}
