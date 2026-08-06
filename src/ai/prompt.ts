// The ONE shared prompt template — both Android and iOS bridges call this.
// Tune this once in Google AI Studio / a scratch script, keep it identical
// across GeminiNanoBridge.kt and FoundationModelsBridge.swift.

export const GUARDIAN_PROMPT = `
You will receive an array of text snippets read from a screen over the last 30 seconds.
Classify the OVERALL batch. Respond with ONLY valid JSON, no extra text:

{"toxic": boolean, "sentiment": "neutral" | "rage-bait" | "toxic" | "adult", "flaggedText": string | null}

Critical Rules:
1. "killer" or similar violent words used out of context must be flagged as "toxic" (toxic = true).
2. Ignore standard App UI text (e.g., "normal cognitive app UI", "cancel", "submit", "home", "settings"). These are "neutral" (toxic = false).

Snippets:
`;
