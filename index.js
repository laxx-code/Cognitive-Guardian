import {AppRegistry, LogBox} from 'react-native';
import App from './src/App';
import {name as appName} from './app.json';

// ── Suppress deprecated Flipper JS debugger warning ─────────────────
// React Native 0.74 removed Flipper support. The dev-menu "Debug"
// button still tries the old flipper:// URL scheme and emits a
// console.warn.  Ignore it — use `j` in the Metro terminal instead
// to open the modern Hermes / Chrome DevTools debugger.
LogBox.ignoreLogs([
  'Attempting to debug JS in Flipper',
]);

AppRegistry.registerComponent(appName, () => App);
