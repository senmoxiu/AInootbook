import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useCourseStore = defineStore('course', () => {
  const courses = ref<any[]>([])
  const currentCourse = ref<any>(null)

  const setCourses = (data: any[]) => {
    courses.value = data
  }

  const setCurrentCourse = (course: any) => {
    currentCourse.value = course
  }

  const clearCourses = () => {
    courses.value = []
    currentCourse.value = null
  }

  return {
    courses,
    currentCourse,
    setCourses,
    setCurrentCourse,
    clearCourses
  }
}, {
  persist: {
    storage: {
      getItem: uni.getStorageSync,
      setItem: uni.setStorageSync
    }
  }
})
