import { defineStore } from 'pinia';
import { store } from '/@/store';
import { getNoteById, getGenerationProgress, triggerGeneration, cancelGeneration } from '/@/api/ainote/note.api';

interface NoteItem {
  id: string;
  noteTitle: string;
  noteContent?: string;
  noteStatus: number;
  isPublic: number;
  aiSummary?: string;
  keywords?: string;
  teachingId?: string;
  studentId?: string;
  tenantId?: number;
  createBy?: string;
  createTime?: string;
  updateTime?: string;
}

interface GenerationState {
  noteId: string;
  progress: number;
  status: 'idle' | 'running' | 'completed' | 'failed';
}

interface NoteState {
  currentNote: NoteItem | null;
  generationMap: Record<string, GenerationState>;
  loading: boolean;
}

export const useNoteStore = defineStore({
  id: 'ainote-note',
  state: (): NoteState => ({
    currentNote: null,
    generationMap: {},
    loading: false,
  }),
  getters: {
    getGenerationState:
      (state) =>
      (noteId: string): GenerationState | undefined =>
        state.generationMap[noteId],
  },
  actions: {
    async fetchNote(id: string) {
      this.loading = true;
      try {
        const res = await getNoteById({ id });
        this.currentNote = res;
      } finally {
        this.loading = false;
      }
    },
    clearCurrentNote() {
      this.currentNote = null;
    },
    async startGeneration(noteId: string, knowledgeId?: string) {
      await triggerGeneration({ noteId, knowledgeId });
      this.generationMap[noteId] = { noteId, progress: 0, status: 'running' };
    },
    async stopGeneration(noteId: string) {
      await cancelGeneration({ noteId });
      const entry = this.generationMap[noteId];
      if (entry) {
        entry.status = 'failed';
      }
    },
    async pollProgress(noteId: string, knowledgeId?: string) {
      try {
        const res = await getGenerationProgress({ noteId, knowledgeId });
        const progress: number = res?.progress ?? 0;
        const entry = this.generationMap[noteId];
        if (entry) {
          entry.progress = progress;
          if (progress >= 100) {
            entry.status = 'completed';
          }
        } else {
          this.generationMap[noteId] = {
            noteId,
            progress,
            status: progress >= 100 ? 'completed' : 'running',
          };
        }
      } catch {
        const entry = this.generationMap[noteId];
        if (entry) {
          entry.status = 'failed';
        }
      }
    },
    clearProgress(noteId: string) {
      delete this.generationMap[noteId];
    },
  },
});

export function useNoteStoreWithOut() {
  return useNoteStore(store);
}
