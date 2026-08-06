import { NativeModules, NativeEventEmitter } from 'react-native';
import { useGuardianStore } from '../store/guardianStore';

// Typed wrapper around GuardianBridge (Swift)
const { GuardianBridge } = NativeModules;
const emitter = GuardianBridge ? new NativeEventEmitter(GuardianBridge) : null;

export const IOSGuardianBridge = {
  start: () => {
    GuardianBridge?.startDemoFeed();
  },
  stop: () => {
    GuardianBridge?.stopDemoFeed();
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

    return () => {
      subBatch.remove();
      subOverlay.remove();
    };
  },
  events: emitter,
};
