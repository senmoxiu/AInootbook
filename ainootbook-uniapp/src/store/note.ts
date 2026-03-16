import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useNoteStore = defineStore('note', () => {
  const notes = ref<any[]>([])
  const currentNote = ref<any>(null)

  const setNotes = (data: any[]) => {
    notes.value = data
  }

  const setCurrentNote = (note: any) => {
    currentNote.value = note
  }

  const clearNotes = () => {
    notes.value = []
    currentNote.value = null
  }

  return {
    notes,
    currentNote,
    setNotes,
    setCurrentNote,
    clearNotes
  }
}, {
  persist: {
    storage: {
      getItem: uni.getStorageSync,
      setItem: uni.setStorageSync
    }
  }
})
