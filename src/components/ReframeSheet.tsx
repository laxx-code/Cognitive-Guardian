import React from 'react';
import { View, Text, TouchableOpacity, StyleSheet, Modal } from 'react-native';
import { useGuardianStore } from '../store/guardianStore';

interface ReframeSheetProps {
  text: string;
  onDismiss?: () => void;
}

export default function ReframeSheet({ text, onDismiss }: ReframeSheetProps) {
  const setClassification = useGuardianStore((s) => s.setClassification);

  const handleDismiss = () => {
    setClassification(null);
    if (onDismiss) {
      onDismiss();
    }
  };

  if (!text) return null;

  return (
    <Modal animationType="slide" transparent={true} visible={Boolean(text)} onRequestClose={handleDismiss}>
      <View style={styles.overlay}>
        <View style={styles.sheetContainer}>
          <View style={styles.indicator} />
          <Text style={styles.badge}>COGNITIVE GUARDIAN INTERVENTION</Text>
          <Text style={styles.title}>Mindful Reframing Suggestion</Text>

          <View style={styles.contentBox}>
            <Text style={styles.reframedText}>{text}</Text>
          </View>

          <Text style={styles.footnote}>
            This content was detected as potentially toxic or rage-inducing on screen.
          </Text>

          <TouchableOpacity style={styles.dismissButton} onPress={handleDismiss}>
            <Text style={styles.dismissButtonText}>Dismiss & Continue</Text>
          </TouchableOpacity>
        </View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  overlay: {
    flex: 1,
    backgroundColor: 'rgba(0, 0, 0, 0.4)',
    justifyContent: 'flex-end',
  },
  sheetContainer: {
    backgroundColor: '#ffffff',
    borderTopLeftRadius: 20,
    borderTopRightRadius: 20,
    padding: 20,
    alignItems: 'center',
    elevation: 10,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: -3 },
    shadowOpacity: 0.2,
    shadowRadius: 5,
  },
  indicator: {
    width: 40,
    height: 4,
    backgroundColor: '#dee2e6',
    borderRadius: 2,
    marginBottom: 12,
  },
  badge: {
    fontSize: 11,
    fontWeight: '700',
    color: '#e03131',
    letterSpacing: 0.8,
    marginBottom: 4,
  },
  title: {
    fontSize: 18,
    fontWeight: '700',
    color: '#212529',
    marginBottom: 12,
  },
  contentBox: {
    backgroundColor: '#fff9db',
    borderColor: '#ffe066',
    borderWidth: 1,
    borderRadius: 10,
    padding: 14,
    width: '100%',
    marginBottom: 12,
  },
  reframedText: {
    fontSize: 14,
    color: '#495057',
    lineHeight: 20,
  },
  footnote: {
    fontSize: 12,
    color: '#868e96',
    textAlign: 'center',
    marginBottom: 16,
  },
  dismissButton: {
    backgroundColor: '#4c6ef5',
    paddingVertical: 12,
    paddingHorizontal: 24,
    borderRadius: 8,
    width: '100%',
    alignItems: 'center',
  },
  dismissButtonText: {
    color: '#ffffff',
    fontWeight: '600',
    fontSize: 15,
  },
});
