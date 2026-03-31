import { defineStore } from 'pinia'

export const useAiProgressStore = defineStore('aiProgress', {
  state: () => ({
    tasks: {} as Record<string, { progress: number; status: string; step: number }>
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
    }
  },
  persist: true
})
