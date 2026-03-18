import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { Course, CourseDetail } from '@/api/course'

export const useCourseStore = defineStore(
  'course',
  () => {
    const courses = ref<Course[]>([])
    const currentCourse = ref<Course | CourseDetail | null>(null)

    const setCourses = (data: Course[]) => {
      courses.value = data
    }

    const setCurrentCourse = (course: Course | CourseDetail | null) => {
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
      clearCourses,
    }
  },
  {
    persist: {
      storage: {
        getItem: uni.getStorageSync,
        setItem: uni.setStorageSync,
      },
    },
  },
)
