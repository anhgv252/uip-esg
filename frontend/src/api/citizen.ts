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

export const getBuildings = () =>
  apiClient.get<BuildingDto[]>('/citizen/buildings').then((r) => r.data)

export const registerCitizen = (data: CitizenRegistrationRequest) =>
  apiClient.post<CitizenRegistrationResponse>('/citizen/register', data).then((r) => r.data)

export const linkHousehold = (data: HouseholdRequest) =>
  apiClient
    .post<CitizenProfileDto>('/citizen/profile/household', data)
    .then((r) => r.data)

export const getCitizenProfile = () =>
  apiClient.get<CitizenProfileDto>('/citizen/profile').then((r) => r.data)

export const registerMeter = (data: MeterRequest) =>
  apiClient.post<MeterDto>('/citizen/meters', data).then((r) => r.data)

export const getInvoices = (params?: { month?: number; year?: number; page?: number; size?: number }) => {
  // When both month and year are provided, use the by-month endpoint (BUG-S3-05-01)
  if (params?.month != null && params?.year != null) {
    return apiClient
      .get<InvoiceDto[]>('/citizen/invoices/by-month', { params: { month: params.month, year: params.year } })
      .then((r): InvoicePage => ({
        content: r.data,
        totalElements: r.data.length,
        totalPages: 1,
        number: 0,
      }))
  }
  return apiClient.get<InvoicePage>('/citizen/invoices', { params }).then((r) => r.data)
}

export const getInvoiceById = (id: string) =>
  apiClient.get<InvoiceDto>(`/citizen/invoices/${id}`).then((r) => r.data)

export const getMeters = () =>
  apiClient.get<MeterDto[]>('/citizen/meters').then((r) => r.data)
