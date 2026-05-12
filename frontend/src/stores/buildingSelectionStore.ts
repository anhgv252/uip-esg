import { create } from 'zustand'
import { persist } from 'zustand/middleware'

export interface SelectedBuilding {
  id: string
  buildingCode: string
  buildingName: string
  tenantId: string
}

interface BuildingSelectionState {
  selectedBuildings: SelectedBuilding[]
  addBuilding: (building: SelectedBuilding) => void
  removeBuilding: (buildingId: string) => void
  clearSelection: () => void
  isSelected: (buildingId: string) => boolean
  maxReached: () => boolean
}

const MAX_BUILDINGS = 5

export const useBuildingSelectionStore = create<BuildingSelectionState>()(
  persist(
    (set, get) => ({
      selectedBuildings: [],

      addBuilding: (building) => {
        const state = get()
        if (state.selectedBuildings.length >= MAX_BUILDINGS) return
        if (state.isSelected(building.id)) return
        set((s) => ({ selectedBuildings: [...s.selectedBuildings, building] }))
      },

      removeBuilding: (buildingId) =>
        set((s) => ({
          selectedBuildings: s.selectedBuildings.filter((b) => b.id !== buildingId),
        })),

      clearSelection: () => set({ selectedBuildings: [] }),

      isSelected: (buildingId) =>
        get().selectedBuildings.some((b) => b.id === buildingId),

      maxReached: () => get().selectedBuildings.length >= MAX_BUILDINGS,
    }),
    {
      name: 'uip_selected_buildings',
    }
  )
)
