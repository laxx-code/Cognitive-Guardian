/**
 * React Native CLI configuration.
 *
 * Disables the deprecated Flipper-based JS debugger so the modern
 * Hermes / Chrome DevTools debugger is used instead.
 *
 * @see https://reactnative.dev/docs/debugging
 */
module.exports = {
  reactNativePath: './node_modules/react-native',
  // Disable all auto-linked Flipper pods / native modules
  dependencies: {},
  project: {
    android: {},
    ios: {},
  },
};
