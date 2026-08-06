import React from 'react';
import { View, Text, TouchableOpacity, StyleSheet, Platform } from 'react-native';
import { useGuardianStore } from '../store/guardianStore';
import { AndroidGuardianBridge } from '../native/AndroidBridge';
import neutral from '../../assets/mockFeeds/neutral.json';
import ragebait from '../../assets/mockFeeds/ragebait.json';
import toxic from '../../assets/mockFeeds/toxic.json';

// Demo safety net: injects mock feed JSON directly into the Zustand buffer,
// bypassing native accessibility hooks entirely.
export default function MockInjector() {
  const addToBuffer = useGuardianStore((s) => s.addToBuffer);
  const clearBuffer = useGuardianStore((s) => s.clearBuffer);
  const setClassification = useGuardianStore((s) => s.setClassification);
  const bufferCount = useGuardianStore((s) => s.buffer.length);

  const injectNeutral = () => {
    neutral.forEach(addToBuffer);
    setClassification({
      toxic: false,
      sentiment: 'neutral',
      flaggedText: null,
    });
  };

  const injectRagebait = () => {
    ragebait.forEach(addToBuffer);
    setClassification({
      toxic: true,
      sentiment: 'rage-bait',
      flaggedText: ragebait[0] || 'Viral outrage post detected',
    });
  };

  const injectToxic = () => {
    toxic.forEach(addToBuffer);
    setClassification({
      toxic: true,
      sentiment: 'toxic',
      flaggedText: toxic[0] || 'Extreme toxic commentary detected',
    });
  };

  const testNativeOverlay = () => {
    if (Platform.OS === 'android') {
      try {
        AndroidGuardianBridge.showNativeOverlay(
          'These people are subhuman and I hate every single one of them.',
          { top: 200, left: 0, bottom: 400, right: 1080 },
        );
      } catch (_e) {}
    }
  };

  return (
    <View style={styles.container}>
      <Text style={styles.header}>Synthetic Feed Injector</Text>
      <Text style={styles.subtitle}>
        Inject mock posts into the 30s rolling buffer to test classification without accessibility events:
      </Text>

      <TouchableOpacity
        style={[styles.button, styles.btnNeutral]}
        onPress={injectNeutral}>
        <Text style={styles.btnText}>Inject Neutral Feed ({neutral.length} posts)</Text>
      </TouchableOpacity>

      <TouchableOpacity
        style={[styles.button, styles.btnRage]}
        onPress={injectRagebait}>
        <Text style={styles.btnText}>Inject Rage-bait Feed ({ragebait.length} posts)</Text>
      </TouchableOpacity>

      <TouchableOpacity
        style={[styles.button, styles.btnToxic]}
        onPress={injectToxic}>
        <Text style={styles.btnText}>Inject Toxic Feed ({toxic.length} posts)</Text>
      </TouchableOpacity>

      {Platform.OS === 'android' && (
        <TouchableOpacity style={[styles.button, styles.btnOverlay]} onPress={testNativeOverlay}>
          <Text style={styles.btnText}>Test Native Overlay (Android)</Text>
        </TouchableOpacity>
      )}

      <TouchableOpacity style={[styles.button, styles.btnClear]} onPress={clearBuffer}>
        <Text style={styles.btnClearText}>Clear Buffer ({bufferCount} items)</Text>
      </TouchableOpacity>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    padding: 16,
    backgroundColor: '#ffffff',
    borderRadius: 12,
    elevation: 2,
  },
  header: {
    fontSize: 16,
    fontWeight: '700',
    color: '#212529',
  },
  subtitle: {
    fontSize: 13,
    color: '#6c757d',
    marginVertical: 8,
  },
  button: {
    paddingVertical: 12,
    paddingHorizontal: 16,
    borderRadius: 8,
    alignItems: 'center',
    marginVertical: 6,
  },
  btnNeutral: {
    backgroundColor: '#339af0',
  },
  btnRage: {
    backgroundColor: '#fcc419',
  },
  btnToxic: {
    backgroundColor: '#ff6b6b',
  },
  btnOverlay: {
    backgroundColor: '#7950f2',
  },
  btnClear: {
    backgroundColor: '#f1f3f5',
    marginTop: 12,
  },
  btnText: {
    color: '#ffffff',
    fontWeight: '600',
    fontSize: 14,
  },
  btnClearText: {
    color: '#495057',
    fontWeight: '600',
    fontSize: 14,
  },
});
