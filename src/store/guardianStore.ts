import { create } from 'zustand';
import { GuardianClassification } from '../ai/responseParser';

export interface BufferEntry {
  text: string;
  timestamp: number;
}

export interface InterventionLogEntry {
  id: string;
  timestamp: number;
  sentiment: GuardianClassification['sentiment'];
  flaggedText: string | null;
}

export interface VisionScanEntry {
  ocrText: string;
  visionLabel: string;
  visionScore: number;
  timestamp: number;
}

export interface SentimentResultEntry {
  toxic: boolean;
  sentiment: string;
  flaggedText: string | null;
  timestamp: number;
}

// Rolling 30s text buffer, sensitivity setting, session intervention counter, active classification
export interface GuardianState {
  entries: BufferEntry[];
  buffer: string[];
  sensitivity: number; // 0-1
  interventionsToday: number;
  interventionLog: InterventionLogEntry[];
  activeClassification: GuardianClassification | null;
  visionScans: VisionScanEntry[];
  sentimentResults: SentimentResultEntry[];
  lastScanTimestamp: number | null;
  addToBuffer: (text: string) => void;
  pruneOlderThan: (now?: number, windowMs?: number) => void;
  clearBuffer: () => void;
  setSensitivity: (val: number) => void;
  incrementInterventions: () => void;
  setClassification: (classification: GuardianClassification | null) => void;
  addVisionScan: (scan: VisionScanEntry) => void;
  addSentimentResult: (result: SentimentResultEntry) => void;
}

export const useGuardianStore = create<GuardianState>((set, get) => ({
  entries: [],
  buffer: [],
  sensitivity: 0.5,
  interventionsToday: 0,
  interventionLog: [],
  activeClassification: null,
  visionScans: [],
  sentimentResults: [],
  lastScanTimestamp: null,

  addToBuffer: (text: string) => {
    if (!text || typeof text !== 'string') return;
    const now = Date.now();
    const newEntry: BufferEntry = { text, timestamp: now };
    const updatedEntries = [...get().entries, newEntry];

    // Prune entries older than 30 seconds
    const windowMs = 30000;
    const validEntries = updatedEntries.filter((e) => now - e.timestamp <= windowMs);

    set({
      entries: validEntries,
      buffer: validEntries.map((e) => e.text),
    });
  },

  pruneOlderThan: (now = Date.now(), windowMs = 30000) => {
    const validEntries = get().entries.filter((e) => now - e.timestamp <= windowMs);
    set({
      entries: validEntries,
      buffer: validEntries.map((e) => e.text),
    });
  },

  clearBuffer: () => set({ entries: [], buffer: [] }),

  setSensitivity: (val: number) => set({ sensitivity: Math.max(0, Math.min(1, val)) }),

  incrementInterventions: () => set((s) => ({ interventionsToday: s.interventionsToday + 1 })),

  setClassification: (classification: GuardianClassification | null) => {
    set({ activeClassification: classification });
    if (classification && classification.toxic) {
      get().incrementInterventions();
      const entry: InterventionLogEntry = {
        id: Date.now().toString(),
        timestamp: Date.now(),
        sentiment: classification.sentiment,
        flaggedText: classification.flaggedText,
      };
      set((s) => ({ interventionLog: [entry, ...s.interventionLog].slice(0, 50) }));
    }
  },

  addVisionScan: (scan: VisionScanEntry) => {
    set((s) => ({
      visionScans: [scan, ...s.visionScans].slice(0, 20),
      lastScanTimestamp: scan.timestamp,
    }));
  },

  addSentimentResult: (result: SentimentResultEntry) => {
    set((s) => ({
      sentimentResults: [result, ...s.sentimentResults].slice(0, 20),
    }));
  },
}));
