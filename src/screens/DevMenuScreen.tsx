import React from 'react';
import { View } from 'react-native';
import MockInjector from '../devtools/MockInjector';

export default function DevMenuScreen() {
  return (
    <View>
      <MockInjector />
    </View>
  );
}
