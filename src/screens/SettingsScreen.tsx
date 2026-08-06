import React from 'react';
import { View, Text, ScrollView, StyleSheet } from 'react-native';
import SensitivitySlider from '../components/SensitivitySlider';
import { useGuardianStore } from '../store/guardianStore';

export default function SettingsScreen() {
  const interventionLog = useGuardianStore((s) => s.interventionLog);
  const interventionsToday = useGuardianStore((s) => s.interventionsToday);

  const formatTime = (ts: number) => {
    const d = new Date(ts);
    return `${d.getHours().toString().padStart(2,'0')}:${d.getMinutes().toString().padStart(2,'0')}`;
  };

  const sentimentColor = (s: string) => {
    if (s === 'toxic') return '#e03131';
    if (s === 'rage-bait') return '#f08c00';
    if (s === 'adult') return '#862e9c';
    return '#2b8a3e';
  };

  return (
    <ScrollView style={styles.container}>
      <SensitivitySlider />

      <View style={styles.card}>
        <Text style={styles.cardTitle}>Privacy Guarantee</Text>
        <View style={styles.privacyRow}>
          <Text style={styles.privacyIcon}>🔒</Text>
          <Text style={styles.privacyText}>Zero network calls — all inference runs on-device</Text>
        </View>
        <View style={styles.privacyRow}>
          <Text style={styles.privacyIcon}>🚫</Text>
          <Text style={styles.privacyText}>No screenshots taken — text nodes only</Text>
        </View>
        <View style={styles.privacyRow}>
          <Text style={styles.privacyIcon}>🏦</Text>
          <Text style={styles.privacyText}>Banking, passwords & DMs are automatically skipped</Text>
        </View>
        <View style={styles.privacyRow}>
          <Text style={styles.privacyIcon}>⚡</Text>
          <Text style={styles.privacyText}>5-second idle throttle prevents battery drain</Text>
        </View>
      </View>

      <View style={styles.card}>
        <Text style={styles.cardTitle}>
          Intervention Log — {interventionsToday} today
        </Text>
        {interventionLog.length === 0 ? (
          <Text style={styles.emptyText}>No interventions recorded this session.</Text>
        ) : (
          interventionLog.map((entry) => (
            <View key={entry.id} style={styles.logRow}>
              <View style={[styles.sentimentDot, { backgroundColor: sentimentColor(entry.sentiment) }]} />
              <View style={styles.logContent}>
                <Text style={styles.logSentiment}>{entry.sentiment.toUpperCase()}</Text>
                <Text style={styles.logTime}>{formatTime(entry.timestamp)}</Text>
              </View>
              {entry.flaggedText && (
                <Text style={styles.logSnippet} numberOfLines={1}>{entry.flaggedText}</Text>
              )}
            </View>
          ))
        )}
      </View>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  card: {
    backgroundColor: '#ffffff',
    borderRadius: 12,
    padding: 16,
    marginBottom: 16,
    elevation: 2,
  },
  cardTitle: {
    fontSize: 16,
    fontWeight: '700',
    color: '#343a40',
    marginBottom: 12,
  },
  privacyRow: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    marginBottom: 10,
  },
  privacyIcon: { fontSize: 16, marginRight: 10, marginTop: 1 },
  privacyText: { flex: 1, fontSize: 13, color: '#495057', lineHeight: 18 },
  emptyText: { fontSize: 13, color: '#adb5bd', fontStyle: 'italic' },
  logRow: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 8,
    borderBottomWidth: 1,
    borderBottomColor: '#f1f3f5',
  },
  sentimentDot: {
    width: 10, height: 10, borderRadius: 5, marginRight: 10,
  },
  logContent: { marginRight: 8 },
  logSentiment: { fontSize: 11, fontWeight: '700', color: '#495057' },
  logTime: { fontSize: 11, color: '#adb5bd' },
  logSnippet: { flex: 1, fontSize: 12, color: '#868e96' },
});
