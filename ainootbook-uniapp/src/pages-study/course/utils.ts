import type { Course, CourseDetail } from '@/api/course'

type RecordValue = Record<string, any>

const RESULT_KEYS = ['result', 'data']
const LIST_KEYS = ['records', 'list', 'items', 'rows']
const CHAPTER_ROOT_KEYS = [
  'chapterTree',
  'chapters',
  'chapterList',
  'catalogTree',
  'catalogList',
  'outlineTree',
  'outlineList',
  'nodes',
  'children',
]
const CHAPTER_CHILD_KEYS = [
  'children',
  'chapterList',
  'chapters',
  'nodes',
  'items',
  'sectionList',
  'lessonList',
]
const CHAPTER_ID_KEYS = ['id', 'chapterId', 'key', 'value']
const CHAPTER_TITLE_KEYS = ['chapterName', 'name', 'title', 'label']
const CHAPTER_CODE_KEYS = ['chapterCode', 'code']
const CHAPTER_DESC_KEYS = ['description', 'summary', 'content', 'append']
const CHAPTER_PROGRESS_KEYS = ['studyProgress', 'progress', 'learnProgress', 'completionRate']

export interface CourseChapterTreeNode {
  id: string
  title: string
  code?: string
  description?: string
  progress: number
  children: CourseChapterTreeNode[]
  leaf: boolean
  raw: RecordValue
}

function isRecord(value: unknown): value is RecordValue {
  return Object.prototype.toString.call(value) === '[object Object]'
}

function pickFirst<T>(source: RecordValue, keys: string[]): T | undefined {
  for (const key of keys) {
    const value = source[key]
    if (value !== undefined && value !== null && value !== '') {
      return value as T
    }
  }

  return undefined
}

function sleep(delay: number) {
  return new Promise((resolve) => {
    setTimeout(resolve, delay)
  })
}

export function getErrorMessage(error: unknown, fallback = '请求失败') {
  if (error instanceof Error && error.message) {
    return error.message
  }

  if (typeof error === 'string' && error.trim()) {
    return error
  }

  return fallback
}

export async function withAutoRetry<T>(
  requester: () => Promise<T>,
  options: { retries?: number; baseDelay?: number } = {},
) {
  const retries = options.retries ?? 3
  const baseDelay = options.baseDelay ?? 1000
  let lastError: unknown = null

  for (let attempt = 0; attempt < retries; attempt += 1) {
    try {
      return await requester()
    } catch (error) {
      lastError = error

      if (attempt < retries - 1) {
        await sleep(baseDelay * Math.pow(2, attempt))
      }
    }
  }

  throw lastError instanceof Error ? lastError : new Error(getErrorMessage(lastError))
}

export function unwrapCourseResponse<T>(payload: unknown) {
  if (isRecord(payload)) {
    if ('success' in payload && payload.success === false) {
      throw new Error(String(payload.message || '请求失败'))
    }

    const result = pickFirst<T>(payload, RESULT_KEYS)
    if (result !== undefined) {
      return result
    }
  }

  return payload as T
}

export function normalizeProgress(value: unknown) {
  const numericValue = Number(value ?? 0)

  if (!Number.isFinite(numericValue) || numericValue <= 0) {
    return 0
  }

  const normalizedValue = numericValue > 0 && numericValue < 1 ? numericValue * 100 : numericValue
  return Math.min(100, Math.max(0, Math.round(normalizedValue)))
}

export function normalizeCourseListResponse(payload: unknown) {
  const result = unwrapCourseResponse<Course[] | RecordValue>(payload)

  if (Array.isArray(result)) {
    return {
      records: result as Course[],
      total: result.length,
    }
  }

  if (!isRecord(result)) {
    return {
      records: [] as Course[],
      total: 0,
    }
  }

  const records = pickFirst<Course[]>(result, LIST_KEYS) ?? []
  const totalValue = result.total ?? result.count ?? records.length
  const total = Number.isFinite(Number(totalValue)) ? Number(totalValue) : records.length

  return {
    records: Array.isArray(records) ? records : [],
    total,
  }
}

export function normalizeCourseDetailResponse(payload: unknown) {
  const result = unwrapCourseResponse<CourseDetail | RecordValue>(payload)
  return (isRecord(result) ? result : {}) as CourseDetail
}

export function normalizeChapterTree(detail: CourseDetail | RecordValue | unknown) {
  const source = unwrapCourseResponse<RecordValue | CourseDetail | CourseChapterTreeNode[]>(detail)

  if (Array.isArray(source)) {
    return normalizeChapterNodes(source)
  }

  if (!isRecord(source)) {
    return [] as CourseChapterTreeNode[]
  }

  for (const key of CHAPTER_ROOT_KEYS) {
    if (Array.isArray(source[key])) {
      return normalizeChapterNodes(source[key], key)
    }
  }

  return [] as CourseChapterTreeNode[]
}

function normalizeChapterNodes(nodes: unknown[], parentKey = 'chapter'): CourseChapterTreeNode[] {
  return nodes.map((node, index) => {
    const source = isRecord(node) ? node : {}
    const title = pickFirst<string>(source, CHAPTER_TITLE_KEYS) ?? `章节 ${index + 1}`
    const id = String(
      pickFirst<string | number>(source, CHAPTER_ID_KEYS) ?? `${parentKey}-${index}`,
    )

    let childrenSource: unknown[] = []
    for (const key of CHAPTER_CHILD_KEYS) {
      if (Array.isArray(source[key])) {
        childrenSource = source[key]
        break
      }
    }

    const children = normalizeChapterNodes(childrenSource, id)

    return {
      id,
      title,
      code: pickFirst<string>(source, CHAPTER_CODE_KEYS),
      description: pickFirst<string>(source, CHAPTER_DESC_KEYS),
      progress: normalizeProgress(pickFirst<number | string>(source, CHAPTER_PROGRESS_KEYS)),
      children,
      leaf: children.length === 0,
      raw: source,
    }
  })
}

export function countLeafChapters(nodes: CourseChapterTreeNode[]) {
  let total = 0

  const walk = (treeNodes: CourseChapterTreeNode[]) => {
    treeNodes.forEach((node) => {
      if (node.children.length) {
        walk(node.children)
      } else {
        total += 1
      }
    })
  }

  walk(nodes)

  return total
}
