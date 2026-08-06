import { useGuardianStore } from '../src/store/guardianStore';

describe('useGuardianStore', () => {
  beforeEach(() => {
    useGuardianStore.getState().clearBuffer();
    useGuardianStore.setState({ interventionsToday: 0, sensitivity: 0.5, activeClassification: null });
  });

  test('adds items to buffer and preserves rolling window', () => {
    const store = useGuardianStore.getState();
    store.addToBuffer('Post 1');
    store.addToBuffer('Post 2');

    expect(useGuardianStore.getState().buffer).toEqual(['Post 1', 'Post 2']);
  });

  test('prunes items older than 30 seconds', () => {
    const now = Date.now();

    useGuardianStore.setState({
      entries: [
        { text: 'Old Post', timestamp: now - 35000 },
        { text: 'Recent Post', timestamp: now - 5000 },
      ],
      buffer: ['Old Post', 'Recent Post'],
    });

    useGuardianStore.getState().pruneOlderThan(now, 30000);

    expect(useGuardianStore.getState().buffer).toEqual(['Recent Post']);
  });

  test('clearBuffer empties the buffer and entries', () => {
    useGuardianStore.getState().addToBuffer('Sample');
    useGuardianStore.getState().clearBuffer();

    expect(useGuardianStore.getState().buffer).toEqual([]);
  });

  test('interventionsToday increments only when called', () => {
    expect(useGuardianStore.getState().interventionsToday).toBe(0);
    useGuardianStore.getState().addToBuffer('Post');
    expect(useGuardianStore.getState().interventionsToday).toBe(0);

    useGuardianStore.getState().incrementInterventions();
    expect(useGuardianStore.getState().interventionsToday).toBe(1);
  });

  test('setSensitivity clamps values between 0 and 1', () => {
    useGuardianStore.getState().setSensitivity(1.5);
    expect(useGuardianStore.getState().sensitivity).toBe(1.0);

    useGuardianStore.getState().setSensitivity(-0.5);
    expect(useGuardianStore.getState().sensitivity).toBe(0.0);

    useGuardianStore.getState().setSensitivity(0.7);
    expect(useGuardianStore.getState().sensitivity).toBe(0.7);
  });
});
