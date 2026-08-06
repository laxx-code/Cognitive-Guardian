import React from 'react';
import { View, Text, TouchableOpacity, StyleSheet, Platform } from 'react-native';
import { useGuardianStore } from '../store/guardianStore';
import { AndroidGuardianBridge } from '../native/AndroidBridge';

export default function SensitivitySlider() {
  const sensitivity = useGuardianStore((s) => s.sensitivity);
  const setSensitivity = useGuardianStore((s) => s.setSensitivity);

  const updateSensitivity = (val: number) => {
    setSensitivity(val);
    // Propagate to native classifier so it gates interventions immediately
    if (Platform.OS === 'android') {
      try { AndroidGuardianBridge.setThreshold(val); } catch (_e) {}
    }
  };

  const label = sensitivity < 0.3 ? 'Lenient' : sensitivity > 0.7 ? 'Aggressive' : 'Balanced';

  return (
    <View style={styles.container}>
      <Text style={styles.title}>Intervention Sensitivity</Text>
      <Text style={styles.valueText}>
        {(sensitivity * 100).toFixed(0)}% — <Text style={styles.label}>{label}</Text>
      </Text>
      <Text style={styles.hint}>
        Low = fewer alerts  ·  High = flag rage-bait too
      </Text>

      <View style={styles.controlsRow}>
        <TouchableOpacity
          style={styles.button}
          onPress={() => updateSensitivity(sensitivity - 0.1)}>
          <Text style={styles.buttonText}>− 10%</Text>
        </TouchableOpacity>
        <TouchableOpacity
          style={styles.button}
          onPress={() => updateSensitivity(0.5)}>
          <Text style={styles.buttonText}>Reset</Text>
        </TouchableOpacity>
        <TouchableOpacity
          style={styles.button}
          onPress={() => updateSensitivity(sensitivity + 0.1)}>
          <Text style={styles.buttonText}>+ 10%</Text>
        </TouchableOpacity>
      </View>
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
  title: {
    fontSize: 16,
    fontWeight: '700',
    color: '#343a40',
  },
  valueText: {
    fontSize: 14,
    color: '#495057',
    marginVertical: 8,
  },
  controlsRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginTop: 8,
  },
  label: {
    color: '#4c6ef5',
    fontWeight: '700',
  },
  hint: {
    fontSize: 12,
    color: '#adb5bd',
    marginBottom: 8,
  },
  button: {
    backgroundColor: '#e7f5ff',
    paddingVertical: 8,
    paddingHorizontal: 12,
    borderRadius: 6,
  },
  buttonText: {
    color: '#1971c2',
    fontWeight: '600',
    fontSize: 13,
  },
});
