import React, { useEffect, useState } from 'react';
import { View, Text, StyleSheet, ScrollView, Platform, TouchableOpacity, Animated } from 'react-native';
import { useGuardianStore } from '../store/guardianStore';
import { AndroidGuardianBridge } from '../native/AndroidBridge';
import { IOSGuardianBridge } from '../native/iOSBridge';
import ReframeSheet from '../components/ReframeSheet';

function PulsingDot() {
  const [opacity] = useState(new Animated.Value(1));

  useEffect(() => {
    const pulse = Animated.loop(
      Animated.sequence([
        Animated.timing(opacity, { toValue: 0.3, duration: 800, useNativeDriver: true }),
        Animated.timing(opacity, { toValue: 1, duration: 800, useNativeDriver: true }),
      ]),
    );
    pulse.start();
    return () => pulse.stop();
  }, [opacity]);

  return <Animated.Text style={[styles.pulsingDot, { opacity }]}>●</Animated.Text>;
}

export default function HomeScreen() {
  const buffer = useGuardianStore((s) => s.buffer);
  const sensitivity = useGuardianStore((s) => s.sensitivity);
  const interventionsToday = useGuardianStore((s) => s.interventionsToday);
  const activeClassification = useGuardianStore((s) => s.activeClassification);
  const visionScans = useGuardianStore((s) => s.visionScans);
  const sentimentResults = useGuardianStore((s) => s.sentimentResults);
  const lastScanTimestamp = useGuardianStore((s) => s.lastScanTimestamp);
  const [showServiceBanner, setShowServiceBanner] = useState(false);

  useEffect(() => {
    const bridge = Platform.OS === 'android' ? AndroidGuardianBridge : IOSGuardianBridge;

    let unsubscribe: (() => void) | undefined;

    try {
      bridge.start();
      // Wire native events → Zustand store (onTextBatch + onOverlayNeeded + onVisionScan + onVisionSentiment)
      unsubscribe = bridge.subscribe();
      // Show setup banner if bridge is present but service not yet confirmed running
      if (Platform.OS === 'android') {
        setShowServiceBanner(true);
      }
    } catch (_e) {
      // Bridge unlinked or running in test/emulator environment
    }

    return () => {
      try {
        bridge.stop();
        unsubscribe?.();
      } catch (_e) {
        // Bridge unlinked or running in test/emulator environment
      }
    };
  }, []);

  const formatTime = (ts: number) => {
    const d = new Date(ts);
    return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' });
  };

  const getSentimentColor = (sentiment: string) => {
    switch (sentiment) {
      case 'toxic': return '#e03131';
      case 'rage-bait': return '#f76707';
      default: return '#2b8a3e';
    }
  };

  const getSentimentEmoji = (sentiment: string) => {
    switch (sentiment) {
      case 'toxic': return '🔴';
      case 'rage-bait': return '🟠';
      default: return '🟢';
    }
  };

  return (
    <ScrollView style={styles.container}>
      {showServiceBanner && Platform.OS === 'android' && (
        <View style={styles.banner}>
          <Text style={styles.bannerText}>
            ⚙️ Enable Guardian in Accessibility Settings to start live protection.
          </Text>
          <TouchableOpacity onPress={() => setShowServiceBanner(false)}>
            <Text style={styles.bannerDismiss}>✕</Text>
          </TouchableOpacity>
        </View>
      )}

      {Platform.OS === 'android' && (
        <TouchableOpacity 
          style={styles.captureButton}
          onPress={() => AndroidGuardianBridge.startMediaProjection()}
        >
          <Text style={styles.captureButtonText}>🎥 Start Screen Capture (Videos/Images)</Text>
        </TouchableOpacity>
      )}

      <View style={styles.card}>
        <View style={styles.statusRow}>
          <PulsingDot />
          <Text style={styles.cardTitle}> Guardian Status</Text>
        </View>
        <Text style={styles.statusBadge}>
          {lastScanTimestamp ? '● SCANNING — REAL-TIME' : '● ACTIVE — ON-DEVICE PRIVATE'}
        </Text>
        <Text style={styles.statusSub}>
          {lastScanTimestamp
            ? `Last scan: ${formatTime(lastScanTimestamp)} · ${visionScans.length} scans completed`
            : 'Zero network calls · All inference on-device'}
        </Text>
      </View>

      <View style={styles.metricsRow}>
        <View style={styles.metricCard}>
          <Text style={styles.metricValue}>{buffer.length}</Text>
          <Text style={styles.metricLabel}>Buffered</Text>
        </View>
        <View style={styles.metricCard}>
          <Text style={styles.metricValue}>{interventionsToday}</Text>
          <Text style={styles.metricLabel}>Interventions</Text>
        </View>
        <View style={styles.metricCard}>
          <Text style={styles.metricValue}>{visionScans.length}</Text>
          <Text style={styles.metricLabel}>Vision Scans</Text>
        </View>
        <View style={styles.metricCard}>
          <Text style={styles.metricValue}>{(sensitivity * 100).toFixed(0)}%</Text>
          <Text style={styles.metricLabel}>Sensitivity</Text>
        </View>
      </View>

      {/* Live Vision AI Activity Feed */}
      <View style={styles.card}>
        <Text style={styles.cardTitle}>🧠 Live AI Activity Feed</Text>
        {visionScans.length === 0 ? (
          <Text style={styles.emptyText}>
            Waiting for screen activity… Stop scrolling for 5 seconds to trigger a scan.
          </Text>
        ) : (
          visionScans.slice(0, 5).map((scan, index) => {
            // Find the matching sentiment result for this scan
            const matchingSentiment = sentimentResults.find(
              (s) => Math.abs(s.timestamp - scan.timestamp) < 10000,
            );
            return (
              <View key={`${scan.timestamp}-${index}`} style={styles.activityCard}>
                <View style={styles.activityHeader}>
                  <Text style={styles.activityTime}>🕐 {formatTime(scan.timestamp)}</Text>
                  <View style={styles.labelBadge}>
                    <Text style={styles.labelBadgeText}>📷 {scan.visionLabel}</Text>
                  </View>
                </View>

                {scan.ocrText.length > 0 ? (
                  <View style={styles.ocrBox}>
                    <Text style={styles.ocrLabel}>📝 OCR Extracted Text:</Text>
                    <Text style={styles.ocrText} numberOfLines={3}>
                      {scan.ocrText}
                    </Text>
                  </View>
                ) : (
                  <View style={styles.ocrBox}>
                    <Text style={styles.ocrLabelEmpty}>📝 No text found in this frame</Text>
                  </View>
                )}

                {matchingSentiment ? (
                  <View style={[styles.sentimentBox, { borderLeftColor: getSentimentColor(matchingSentiment.sentiment) }]}>
                    <Text style={styles.sentimentLabel}>
                      {getSentimentEmoji(matchingSentiment.sentiment)} Sentiment:{' '}
                      <Text style={[styles.sentimentValue, { color: getSentimentColor(matchingSentiment.sentiment) }]}>
                        {matchingSentiment.sentiment.toUpperCase()}
                      </Text>
                    </Text>
                    {matchingSentiment.toxic && (
                      <Text style={styles.toxicWarning}>⚠️ TOXIC — Overlay triggered!</Text>
                    )}
                  </View>
                ) : (
                  <View style={styles.sentimentBox}>
                    <Text style={styles.sentimentPending}>⏳ Analyzing sentiment...</Text>
                  </View>
                )}
              </View>
            );
          })
        )}
      </View>

      {/* Recent Text Snippets */}
      <View style={styles.card}>
        <Text style={styles.cardTitle}>Recent Screen Snippets ({buffer.length})</Text>
        {buffer.length === 0 ? (
          <Text style={styles.emptyText}>No text captured in rolling 30s buffer yet.</Text>
        ) : (
          buffer.slice(-5).map((item, index) => (
            <View key={index} style={styles.snippetBox}>
              <Text style={styles.snippetText}>{item}</Text>
            </View>
          ))
        )}
      </View>

      {activeClassification && activeClassification.toxic && (
        <ReframeSheet
          text={
            activeClassification.flaggedText ||
            'Rage-bait content detected. Take a deep breath and pause before scrolling further.'
          }
        />
      )}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  card: {
    backgroundColor: '#ffffff',
    borderRadius: 12,
    padding: 16,
    marginBottom: 16,
    elevation: 2,
  },
  statusRow: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 4,
  },
  pulsingDot: {
    color: '#2b8a3e',
    fontSize: 18,
  },
  cardTitle: {
    fontSize: 16,
    fontWeight: '700',
    color: '#343a40',
    marginBottom: 8,
  },
  statusBadge: {
    color: '#2b8a3e',
    fontWeight: '700',
    fontSize: 13,
    marginBottom: 4,
  },
  statusSub: {
    fontSize: 11,
    color: '#868e96',
  },
  banner: {
    backgroundColor: '#fff3bf',
    borderRadius: 10,
    padding: 12,
    marginBottom: 12,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  bannerText: {
    flex: 1,
    fontSize: 13,
    color: '#664d03',
    lineHeight: 18,
  },
  bannerDismiss: {
    fontSize: 16,
    color: '#664d03',
    marginLeft: 8,
    fontWeight: '700',
  },
  captureButton: {
    backgroundColor: '#4c6ef5',
    padding: 14,
    borderRadius: 10,
    marginBottom: 12,
    alignItems: 'center',
    shadowColor: '#4c6ef5',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.3,
    shadowRadius: 4,
    elevation: 3,
  },
  captureButtonText: {
    color: '#fff',
    fontSize: 14,
    fontWeight: '700',
  },
  metricsRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: 16,
  },
  metricCard: {
    flex: 1,
    backgroundColor: '#ffffff',
    borderRadius: 12,
    padding: 10,
    alignItems: 'center',
    marginHorizontal: 3,
    elevation: 2,
  },
  metricValue: {
    fontSize: 18,
    fontWeight: '700',
    color: '#4c6ef5',
  },
  metricLabel: {
    fontSize: 10,
    color: '#868e96',
    marginTop: 4,
  },
  emptyText: {
    color: '#adb5bd',
    fontStyle: 'italic',
    marginTop: 4,
  },
  // Live Activity Feed
  activityCard: {
    backgroundColor: '#f8f9fa',
    borderRadius: 10,
    padding: 12,
    marginTop: 10,
    borderLeftWidth: 3,
    borderLeftColor: '#4c6ef5',
  },
  activityHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 8,
  },
  activityTime: {
    fontSize: 12,
    color: '#495057',
    fontWeight: '600',
  },
  labelBadge: {
    backgroundColor: '#e7f5ff',
    borderRadius: 6,
    paddingHorizontal: 8,
    paddingVertical: 3,
  },
  labelBadgeText: {
    fontSize: 11,
    color: '#1971c2',
    fontWeight: '600',
  },
  ocrBox: {
    backgroundColor: '#ffffff',
    borderRadius: 6,
    padding: 8,
    marginBottom: 6,
  },
  ocrLabel: {
    fontSize: 11,
    fontWeight: '700',
    color: '#495057',
    marginBottom: 4,
  },
  ocrLabelEmpty: {
    fontSize: 11,
    color: '#adb5bd',
    fontStyle: 'italic',
  },
  ocrText: {
    fontSize: 12,
    color: '#343a40',
    lineHeight: 17,
  },
  sentimentBox: {
    borderLeftWidth: 3,
    borderLeftColor: '#dee2e6',
    paddingLeft: 8,
    paddingVertical: 4,
  },
  sentimentLabel: {
    fontSize: 12,
    fontWeight: '600',
    color: '#495057',
  },
  sentimentValue: {
    fontWeight: '800',
  },
  sentimentPending: {
    fontSize: 12,
    color: '#adb5bd',
    fontStyle: 'italic',
  },
  toxicWarning: {
    fontSize: 12,
    color: '#e03131',
    fontWeight: '700',
    marginTop: 3,
  },
  snippetBox: {
    backgroundColor: '#f1f3f5',
    borderRadius: 6,
    padding: 10,
    marginTop: 8,
  },
  snippetText: {
    fontSize: 13,
    color: '#495057',
  },
});
