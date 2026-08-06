# Cognitive Guardian — 5-Day Prototype Implementation Roadmap

**Team size:** 2
**Timeline:** 5 days
**Goal:** A working, demoable prototype showing on-device AI detecting rage-bait/toxic content and intervening with an overlay — on Android (real, live) and iOS (real pipeline, in-app simulated feed).

---

## 0. Reality Check (read this first)

| Claim in original spec | Status | What we're actually doing |
|---|---|---|
| Android AccessibilityService reads other apps live | ✅ Real, well-supported | Build for real — this is our headline demo |
| Android Gemini Nano via ML Kit GenAI Prompt API | ✅ Real, but **Alpha** | Works, but only on Tensor/Snapdragon-NPU/MediaTek-APU devices (Pixel 8+/S24+). Needs a real test device, not an emulator |
| iOS reads other apps' screen content | ❌ Not possible via public API | iOS sandboxing blocks this by design — no workaround without jailbreak |
| iOS Foundation Models (on-device LLM) | ✅ Real (iOS 18.1+, A17 Pro+) | We use this for real inference, just against our own in-app demo feed instead of a 3rd-party app |
| 30s buffering + live overlay tracking | ⚠️ Latency mismatch | Content is already scrolled past by the time it's flagged — fine for a reframe notification, not for "instant blocking" |

**Demo framing that's honest and still impressive:** "On Android, we intercept live in any app. On iOS, Apple's own sandboxing prevents cross-app reading — so we show the identical on-device AI pipeline running inside a demo feed we built, proving the model and overlay logic both work, with a clear note on the platform constraint." Judges will respect this far more than a fudged claim.

**Backup plan (non-negotiable):** Record a clean screen capture of the live Android demo on Day 4 evening, even if you plan to demo live on Day 5. Live demos fail. Never present without a backup video ready to switch to.

---

## 1. Architecture

### Android (primary, live, real cross-app)
```
[Instagram/Twitter/News app on screen]
        │  AccessibilityNodeInfo (text + boundsInScreen)
        ▼
[Kotlin AccessibilityService]
        │  JNI/bridge event
        ▼
[React Native background module]
        │
        ▼
[Zustand rolling 30s buffer]
        │  batch of strings
        ▼
[ML Kit GenAI Prompt API → Gemini Nano via AICore]
        │  { sentiment, toxic: bool, flaggedText }
        ▼
   toxic? ──No──> keep buffering
        │
       Yes
        ▼
[Get Rect bounds of flagged node]
        ▼
[WindowManager TYPE_ACCESSIBILITY_OVERLAY]
   → black-box overlay OR bottom-sheet reframe text
```

### iOS (real pipeline, in-app simulated feed)
```
[Our own demo feed screen (SwiftUI/RN)]
        │  text content we control (real UIKit/SwiftUI view tree — legitimately readable, it's our own app)
        ▼
[Swift bridge]
        ▼
[React Native buffer (Zustand)]
        ▼
[Apple Foundation Models — SystemLanguageModel, on-device]
        │  { sentiment, toxic: bool, flaggedText }
        ▼
[Local overlay view drawn over the flagged cell in our own feed]
```

### Shared layer
- React Native app: settings dashboard, dev-menu mock injector, Zustand store, cross-platform UI (bottom sheet, toggles, sliders)
- Native bridges (Kotlin / Swift) are thin: extract text + bounds, call platform LLM, return JSON

---

## 2. Data Flow (step by step)

1. **Extract** — Native layer emits raw text strings (+ bounds, Android only) as the user scrolls.
2. **Batch** — Zustand buffers strings for a rolling 30s window (or N screen-fulls of text, whichever comes first — tune during testing).
3. **Infer** — Buffer sent to on-device LLM with a strict, narrow prompt: return only `{sentiment: string, toxic: boolean, flaggedText: string|null}`. Nothing else.
4. **Decide** — If `toxic == true`, trigger intervention.
5. **Act** —
   - Android: fetch `boundsInScreen` of the matched node → draw `WindowManager` overlay at those coordinates, OR fire a bottom-sheet with AI-generated reframing text.
   - iOS: overlay drawn over the matching cell in the in-app demo feed.
6. **Log (local only)** — Store event locally for the settings dashboard ("You avoided 12 rage-bait posts today"). No network calls, ever — this is a selling point, keep it true.

---

## 3. Five-Day Plan & Task Split

Two roles, split by platform-depth vs. AI/shared-layer, so you're not blocking each other.

- **Person A — "Native/Android Lead"**: Kotlin, AccessibilityService, WindowManager overlays, Android device setup, ML Kit GenAI integration.
- **Person B — "AI/Cross-platform Lead"**: React Native shell, Zustand store, prompt engineering (both platforms), Swift bridge + Foundation Models, dev-menu mock injector, settings UI.

| Day | Person A (Android) | Person B (RN + AI + iOS) |
|---|---|---|
| **Day 1** | Set up Pixel 8+/S24 test device, enable Developer Options, get AICore + ML Kit GenAI Prompt API sample running (just call it with a hardcoded string, confirm it returns text). Build minimal AccessibilityService that logs node text to Logcat. | Scaffold RN app (screens: Home, Settings, Dev Menu). Set up Zustand store with a 30s buffer stub. Start prompt engineering in Google AI Studio: draft the strict JSON-only sentiment/toxicity prompt, test against 15-20 synthetic sample posts (mix of neutral, rage-bait, toxic, adult-flagged) until false positives are low. |
| **Day 2** | Wire AccessibilityService → RN bridge (native module) so real screen text reaches the JS buffer. Get `boundsInScreen` extraction working and logged. | Build the dev-menu mock injector (paste/select mock JSON feed items into the Zustand buffer, bypassing native hooks) — this is your demo safety net, per the original spec's own testing section. Wire buffer → Gemini Nano prompt call → parse JSON response. |
| **Day 3** | Implement the actual overlay: `WindowManager` + `TYPE_ACCESSIBILITY_OVERLAY`, positioned using bounds from Day 2. Test black-box overlay + bottom-sheet reframe UI on top of a real Instagram/Twitter scroll session. | Start iOS track: scaffold SwiftUI demo feed screen (10-15 built-in sample posts, some flagged). Get Foundation Models `SystemLanguageModel` running with the same prompt logic, returning the same JSON shape. Reuse the shared prompt from Day 1 — don't rewrite from scratch. |
| **Day 4** | Add secure-app blacklisting (skip banking/password apps via `FLAG_SECURE` check). Add scroll-based throttle (pause extraction after 5s idle). Full run-through on live apps, fix bounds/timing bugs. **Record backup demo video tonight.** | Wire iOS overlay drawing over the in-app feed cell. Polish settings dashboard (sensitivity slider, toggle). Integration pass: make sure both platforms' bridges emit into the *same* shared buffer/inference module rather than duplicated code. |
| **Day 5** | Final bug fixes, battery/perf sanity check, rehearse live-scroll demo on the actual device you'll present with. | Rehearse the full demo script (see below), prep the mock-injector as instant fallback if live scrolling misfires, finalize slides covering the iOS platform-constraint explanation. |

---

## 4. Demo Script (recommended flow)

1. Open a real feed (Twitter/Reddit) on Android, scroll normally for ~20s through neutral content — nothing happens (proves it's not just blanket-censoring everything).
2. Scroll into a pre-seeded rage-bait/toxic thread (use a test account or saved thread you control, so timing is predictable) — within ~30s, overlay/bottom-sheet triggers live.
3. Show the settings dashboard — sensitivity slider, "zero network permission" line in the manifest as a trust point.
4. Switch to iOS: explain the platform constraint in one sentence, show the same AI pipeline running against your in-app demo feed with an identical overlay trigger.
5. If Android live-scroll misbehaves on stage: immediately fall back to the dev-menu mock injector — same visual result, zero risk.

---

## 5. Key Risks & Mitigations

- **AICore not initialized on demo device** → Set up and test the exact device 3+ days before, not day-of. Call `checkFeatureStatus()` early and handle the wait.
- **Live scroll demo timing is unpredictable** → Use a thread/account you control with known content, rehearsed timing, and the mock injector as instant fallback.
- **iOS "real" claim overreach** → Be upfront in the pitch; it reads as more credible, not less.
- **Prompt false positives/negatives** → Budget real time on Day 1 in AI Studio before writing any native code — this is the actual "hard part" of the project, not the plumbing.

---

## 6. App Name Suggestions

- **Undoom** — short, plays directly on "doomscrolling"
- **Scrollshield**
- **Mindloop**
- **Outrage OFF**
- **Quiet Feed**
- **Circuit Breaker** (for the mind — nice double meaning with "breaking the loop")

My pick: **Undoom** — short, memorable, instantly explains the pitch in one word for a demo audience.
