import { NativeModules, NativeEventEmitter } from 'react-native';
import { useGuardianStore } from '../store/guardianStore';

// Typed wrapper around GuardianNativeModule (Kotlin)
const { GuardianNativeModule } = NativeModules;
const emitter = GuardianNativeModule ? new NativeEventEmitter(GuardianNativeModule) : null;

export const AndroidGuardianBridge = {
  start: () => {
    GuardianNativeModule?.startExtraction();
  },
  stop: () => {
    GuardianNativeModule?.stopExtraction();
  },
  startMediaProjection: () => {
    GuardianNativeModule?.startMediaProjection();
  },
  setThreshold: (sensitivity: number) => {
    GuardianNativeModule?.setThreshold(sensitivity);
  },
  showNativeOverlay: (
    flaggedText: string,
    rect = { top: 200, left: 0, bottom: 600, right: 1080 },
  ) => {
    GuardianNativeModule?.showNativeOverlay(
      flaggedText,
      rect.top,
      rect.left,
      rect.bottom,
      rect.right,
    );
  },
  subscribe: () => {
    if (!emitter) return () => {};

    const subBatch = emitter.addListener('onTextBatch', (texts: string[]) => {
      if (Array.isArray(texts)) {
        texts.forEach((t) => useGuardianStore.getState().addToBuffer(t));
      }
    });

    const subOverlay = emitter.addListener('onOverlayNeeded', (event: { flaggedText: string }) => {
      useGuardianStore.getState().setClassification({
        toxic: true,
        sentiment: 'toxic',
        flaggedText: event?.flaggedText || null,
      });
    });

    // Real-time vision scan results (OCR + Image Classification)
    const subVisionScan = emitter.addListener('onVisionScan', (event: {
      ocrText: string;
      visionLabel: string;
      visionScore: number;
      timestamp: number;
    }) => {
      useGuardianStore.getState().addVisionScan({
        ocrText: event.ocrText || '',
        visionLabel: event.visionLabel || 'unknown',
        visionScore: event.visionScore || 0,
        timestamp: event.timestamp || Date.now(),
      });
    });

    // Real-time sentiment analysis results from MobileBERT
    const subVisionSentiment = emitter.addListener('onVisionSentiment', (event: {
      result: string;
      timestamp: number;
    }) => {
      try {
        const parsed = JSON.parse(event.result);
        useGuardianStore.getState().addSentimentResult({
          toxic: parsed.toxic || false,
          sentiment: parsed.sentiment || 'neutral',
          flaggedText: parsed.flaggedText || null,
          timestamp: event.timestamp || Date.now(),
        });
      } catch (_e) {
        // Ignore malformed JSON
      }
    });

    return () => {
      subBatch.remove();
      subOverlay.remove();
      subVisionScan.remove();
      subVisionSentiment.remove();
    };
  },
  events: emitter,
};
