import React, { useState } from 'react';
import {
  SafeAreaView,
  View,
  Text,
  TouchableOpacity,
  StyleSheet,
  StatusBar,
} from 'react-native';
import HomeScreen from './screens/HomeScreen';
import SettingsScreen from './screens/SettingsScreen';
import DevMenuScreen from './screens/DevMenuScreen';

type ScreenName = 'Home' | 'Settings' | 'DevMenu';

export default function App() {
  const [currentScreen, setCurrentScreen] = useState<ScreenName>('Home');

  const renderScreen = () => {
    switch (currentScreen) {
      case 'Home':
        return <HomeScreen />;
      case 'Settings':
        return <SettingsScreen />;
      case 'DevMenu':
        return <DevMenuScreen />;
      default:
        return <HomeScreen />;
    }
  };

  return (
    <SafeAreaView style={styles.container}>
      <StatusBar barStyle="dark-content" backgroundColor="#f8f9fa" />
      <View style={styles.header}>
        <Text style={styles.headerTitle}>Cognitive Guardian</Text>
        <Text style={styles.headerSubtitle}>On-Device Cognitive Protection</Text>
      </View>
      <View style={styles.content}>{renderScreen()}</View>
      <View style={styles.navBar}>
        <TouchableOpacity
          style={[styles.navButton, currentScreen === 'Home' && styles.navButtonActive]}
          onPress={() => setCurrentScreen('Home')}>
          <Text style={[styles.navText, currentScreen === 'Home' && styles.navTextActive]}>
            Home
          </Text>
        </TouchableOpacity>
        <TouchableOpacity
          style={[styles.navButton, currentScreen === 'Settings' && styles.navButtonActive]}
          onPress={() => setCurrentScreen('Settings')}>
          <Text style={[styles.navText, currentScreen === 'Settings' && styles.navTextActive]}>
            Settings
          </Text>
        </TouchableOpacity>
        <TouchableOpacity
          style={[styles.navButton, currentScreen === 'DevMenu' && styles.navButtonActive]}
          onPress={() => setCurrentScreen('DevMenu')}>
          <Text style={[styles.navText, currentScreen === 'DevMenu' && styles.navTextActive]}>
            Dev Menu
          </Text>
        </TouchableOpacity>
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#f8f9fa',
  },
  header: {
    paddingHorizontal: 20,
    paddingVertical: 16,
    backgroundColor: '#ffffff',
    borderBottomWidth: 1,
    borderBottomColor: '#e9ecef',
  },
  headerTitle: {
    fontSize: 22,
    fontWeight: '700',
    color: '#212529',
  },
  headerSubtitle: {
    fontSize: 13,
    color: '#6c757d',
    marginTop: 2,
  },
  content: {
    flex: 1,
    padding: 16,
  },
  navBar: {
    flexDirection: 'row',
    height: 60,
    backgroundColor: '#ffffff',
    borderTopWidth: 1,
    borderTopColor: '#e9ecef',
    elevation: 8,
  },
  navButton: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },
  navButtonActive: {
    borderTopWidth: 2,
    borderTopColor: '#4c6ef5',
  },
  navText: {
    fontSize: 14,
    fontWeight: '500',
    color: '#868e96',
  },
  navTextActive: {
    color: '#4c6ef5',
    fontWeight: '700',
  },
});
