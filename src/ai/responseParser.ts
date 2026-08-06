export interface GuardianClassification {
  toxic: boolean;
  sentiment: 'neutral' | 'rage-bait' | 'toxic' | 'adult';
  flaggedText: string | null;
}

const DEFAULT_CLASSIFICATION: GuardianClassification = {
  toxic: false,
  sentiment: 'neutral',
  flaggedText: null,
};

const ALLOWED_SENTIMENTS = new Set(['neutral', 'rage-bait', 'toxic', 'adult']);

// Strict parse + validate; fall back to a safe default on malformed JSON or type mismatch
export function parseGuardianResponse(raw: string): GuardianClassification {
  if (!raw || typeof raw !== 'string') {
    return DEFAULT_CLASSIFICATION;
  }

  try {
    const parsed = JSON.parse(raw);

    if (typeof parsed !== 'object' || parsed === null) {
      return DEFAULT_CLASSIFICATION;
    }

    if (typeof parsed.toxic !== 'boolean') {
      return DEFAULT_CLASSIFICATION;
    }

    if (typeof parsed.sentiment !== 'string' || !ALLOWED_SENTIMENTS.has(parsed.sentiment)) {
      return DEFAULT_CLASSIFICATION;
    }

    const flaggedText =
      typeof parsed.flaggedText === 'string'
        ? parsed.flaggedText
        : null;

    return {
      toxic: parsed.toxic,
      sentiment: parsed.sentiment as GuardianClassification['sentiment'],
      flaggedText,
    };
  } catch (_e) {
    return DEFAULT_CLASSIFICATION;
  }
}
