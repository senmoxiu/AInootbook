import { defineStore } from 'pinia'

export const useAiProgressStore = defineStore('aiProgress', {
  state: () => ({
    tasks: {} as Record<string, { progress: number; status: string; step: number }>,
    activePollingIds: [] as string[]
  }),
  actions: {
    saveTask(noteId: string, data: { progress: number; status: string; step: number }) {
      this.tasks[noteId] = data
    },
    getTask(noteId: string) {
      return this.tasks[noteId]
    },
    removeTask(noteId: string) {
      delete this.tasks[noteId]
      this.releasePolling(noteId)
    },
    claimPolling(noteId: string): boolean {
      if (this.activePollingIds.includes(noteId)) return false
      this.activePollingIds.push(noteId)
      return true
    },
    releasePolling(noteId: string) {
      const idx = this.activePollingIds.indexOf(noteId)
      if (idx !== -1) this.activePollingIds.splice(idx, 1)
    }
  },
  persist: {
    paths: ['tasks']
  }
})
