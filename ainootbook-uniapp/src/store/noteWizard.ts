import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useNoteWizardStore = defineStore('noteWizard', () => {
  const wizardData = ref<Record<string, any>>({})
  const step = ref<number>(1)

  const setWizardData = (data: Record<string, any>) => {
    wizardData.value = { ...wizardData.value, ...data }
  }
  
  const setStep = (newStep: number) => {
    step.value = newStep
  }

  const clearWizardData = () => {
    wizardData.value = {}
    step.value = 1
  }

  return {
    wizardData,
    step,
    setWizardData,
    setStep,
    clearWizardData
  }
}, {
  persist: {
    storage: {
      getItem: uni.getStorageSync,
      setItem: uni.setStorageSync
    }
  }
})
