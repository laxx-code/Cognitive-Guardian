import { parseGuardianResponse, GuardianClassification } from '../src/ai/responseParser';

describe('parseGuardianResponse', () => {
  const safeDefault: GuardianClassification = {
    toxic: false,
    sentiment: 'neutral',
    flaggedText: null,
  };

  test('parses valid JSON with correct fields correctly', () => {
    const raw = JSON.stringify({
      toxic: true,
      sentiment: 'rage-bait',
      flaggedText: 'You will not believe what happened next!',
    });
    const result = parseGuardianResponse(raw);
    expect(result).toEqual({
      toxic: true,
      sentiment: 'rage-bait',
      flaggedText: 'You will not believe what happened next!',
    });
  });

  test('falls back to safe default on malformed JSON without throwing', () => {
    const raw = '{toxic: true'; // syntax error
    expect(() => {
      const result = parseGuardianResponse(raw);
      expect(result).toEqual(safeDefault);
    }).not.toThrow();
  });

  test('falls back to safe default when toxic field is not boolean', () => {
    const raw = JSON.stringify({
      toxic: 'true',
      sentiment: 'toxic',
      flaggedText: 'Bad text',
    });
    const result = parseGuardianResponse(raw);
    expect(result).toEqual(safeDefault);
  });

  test('falls back to safe default when sentiment is invalid', () => {
    const raw = JSON.stringify({
      toxic: true,
      sentiment: 'unknown-sentiment',
      flaggedText: 'Bad text',
    });
    const result = parseGuardianResponse(raw);
    expect(result).toEqual(safeDefault);
  });

  test('falls back to safe default on empty string input', () => {
    const result = parseGuardianResponse('');
    expect(result).toEqual(safeDefault);
  });

  test('handles null flaggedText correctly when toxic is false', () => {
    const raw = JSON.stringify({
      toxic: false,
      sentiment: 'neutral',
      flaggedText: null,
    });
    const result = parseGuardianResponse(raw);
    expect(result).toEqual(safeDefault);
  });

  // GeminiNanoBridge now emits flaggedIndex so the native overlay can target the
  // exact node. The JS parser must ignore the extra field, not reject the payload.
  test('ignores the native flaggedIndex field without rejecting the payload', () => {
    const raw = JSON.stringify({
      toxic: true,
      sentiment: 'toxic',
      flaggedText: 'Absolute idiots running this country.',
      flaggedIndex: 7,
    });
    const result = parseGuardianResponse(raw);
    expect(result).toEqual({
      toxic: true,
      sentiment: 'toxic',
      flaggedText: 'Absolute idiots running this country.',
    });
    expect(result).not.toHaveProperty('flaggedIndex');
  });
});
