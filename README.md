# Undoom

### On-device AI that helps you pause before the feed takes over.

**Undoom is a privacy-first cognitive safety layer for social feeds that detects rage-bait and toxic content on-device and introduces a contextual intervention before the user engages.**

It does not block the internet.
It does not decide what users should believe.
It creates a moment between **content and reaction**.

> **Detect. Pause. Choose.**

---

## The Problem

Modern feeds are optimized for engagement.

Content that triggers anger, outrage, fear, and strong emotional reactions can keep users scrolling long after they intended to stop.

Traditional moderation focuses on whether content violates a policy.

Undoom focuses on a different question:

> **"Is this content trying to pull me into a reaction?"**

The objective is not censorship.

It is **intentional consumption**.

---

## The Product

Undoom continuously observes content available to the application, analyzes it locally using an on-device language model, and intervenes when content crosses a configurable toxicity or rage-bait threshold.

### Without Undoom

```text
See → React → Scroll → Repeat
```

### With Undoom

```text
See → Analyze → Pause → Choose
```

The intervention can be as simple as a contextual warning or a reframing prompt.

---

## Core Capabilities

### On-Device Content Intelligence

Content is analyzed locally rather than being sent to a remote moderation server.

The prototype evaluates:

* Toxicity
* Sentiment
* Emotional intensity
* Rage-bait signals
* Flagged text

The inference contract is intentionally narrow:

```json
{
  "sentiment": "negative",
  "toxic": true,
  "flaggedText": "..."
}
```

Keeping the output constrained makes the detection layer predictable, fast, and easier to integrate into the intervention system.

---

### Context-Aware Intervention

Undoom does not simply hide content.

When a post is flagged, the system can place an intervention directly over the content or present a reframe.

For example:

```text
┌────────────────────────────────────────┐
│                                        │
│  This content appears designed to      │
│  trigger a strong emotional reaction.  │
│                                        │
│  Take a moment before engaging.        │
│                                        │
│       [ Continue ]   [ Reframe ]       │
│                                        │
└────────────────────────────────────────┘
```

The user remains in control.

---

### Rolling Context Window

Instead of treating every screen event as an isolated prediction, Undoom maintains a short rolling buffer of recently observed content.

This provides additional context while keeping the system responsive.

```text
Recent Content
      │
      ▼
┌─────────────────┐
│ Post A          │
│ Post B          │
│ Post C          │
│ Post D          │
└────────┬────────┘
         │
         ▼
   On-device LLM
         │
         ▼
 Toxicity Decision
         │
         ▼
   Intervention
```

The prototype uses an approximately **30-second rolling window**, tuned according to device performance and scrolling behavior.

---

# Android

## Real Cross-App Prototype

Android is the primary live demonstration platform.

Undoom uses Android's `AccessibilityService` to observe accessible text and screen coordinates from supported applications.

When the local model identifies potentially toxic content, the system can locate the corresponding UI element and render an accessibility overlay over it.

```text
┌─────────────────────────────┐
│       Social Feed           │
│                             │
│  Normal post                │
│                             │
│  Normal post                │
│                             │
│  ┌───────────────────────┐  │
│  │  Potential rage-bait  │  │
│  │                       │  │
│  │  AI intervention      │  │
│  └───────────────────────┘  │
│                             │
└─────────────────────────────┘
```

### Android pipeline

```text
AccessibilityService
        ↓
Text + Screen Bounds
        ↓
Rolling Buffer
        ↓
Gemini Nano
        ↓
Toxicity / Sentiment
        ↓
Matched UI Element
        ↓
Accessibility Overlay
```

This is the project's **primary real-world demo**.

---

# iOS

## Real On-Device AI Pipeline

iOS intentionally takes a different approach.

Public iOS APIs prevent an application from freely inspecting the UI content of another application.

Rather than attempting to bypass the platform sandbox, Undoom demonstrates the same intelligence layer inside a controlled in-app feed.

The application owns the feed content, allowing legitimate content inspection and intervention.

The AI inference runs locally using Apple's Foundation Models.

```text
Undoom Demo Feed
        ↓
Feed Content
        ↓
Foundation Models
        ↓
Toxicity Analysis
        ↓
Flagged Cell
        ↓
In-App Intervention
```

This demonstrates the actual AI capability while respecting Apple's platform security model.

---

# Privacy Architecture

Privacy is not an additional feature.

It is part of the product architecture.

Undoom is designed around:

```text
Content
   ↓
On-device analysis
   ↓
Decision
   ↓
Local intervention
```

rather than:

```text
Content
   ↓
Cloud server
   ↓
Remote analysis
   ↓
Response
```

No remote moderation backend is required for the prototype.

Local statistics can be maintained for the user, such as:

```text
Content analyzed       248
Potentially toxic       31
Interventions           12
Reframes viewed          9
```

The goal is to provide useful feedback without creating a centralized history of what a user consumes.

---

# Platform Reality

A major part of this prototype is proving what can actually be built on current mobile platforms.

| Capability                     |            Android |        iOS |
| ------------------------------ | -----------------: | ---------: |
| On-device LLM                  |                Yes |        Yes |
| Toxicity detection             |                Yes |        Yes |
| Sentiment analysis             |                Yes |        Yes |
| Local processing               |                Yes |        Yes |
| Cross-app content access       | Accessibility APIs | Restricted |
| Cross-app intervention overlay |                Yes | Restricted |
| Controlled in-app feed         |                Yes |        Yes |

Android provides the APIs required for the live cross-app demonstration.

iOS provides the on-device AI capability, but its application sandbox prevents the equivalent cross-app implementation through public APIs.

The prototype treats this as a **platform constraint**, not something to hide.

---

# Technology

| Layer                      | Technology                   |
| -------------------------- | ---------------------------- |
| Mobile framework           | React Native                 |
| Android                    | Kotlin                       |
| iOS                        | Swift / SwiftUI              |
| State                      | Zustand                      |
| Android AI                 | Gemini Nano / Google AI Core |
| Android inference API      | ML Kit GenAI Prompt API      |
| iOS AI                     | Apple Foundation Models      |
| Android system integration | AccessibilityService         |
| Android overlay            | WindowManager                |
| Processing                 | On-device                    |

---

# System Overview

```text
                     UNDOOM
                       │
          ┌────────────┴────────────┐
          │                         │
       ANDROID                     iOS
          │                         │
 AccessibilityService         Controlled Feed
          │                         │
          └────────────┬────────────┘
                       │
                Shared Application
                       │
                 Content Buffer
                       │
                       ▼
                On-device AI
                       │
             ┌─────────┴─────────┐
             │                   │
        Toxic Content       Normal Content
             │                   │
             ▼                   ▼
       Intervention          Continue
             │
             ▼
         User Choice
```

---

# Demo

The prototype is designed around a simple live demonstration.

### 01 — Normal scrolling

The user scrolls through neutral content.

No intervention occurs.

### 02 — Trigger content

A deliberately selected rage-bait or toxic post appears.

### 03 — Local inference

The content is analyzed directly on the device.

### 04 — Intervention

The system identifies the content and presents an intervention.

### 05 — User decides

The user can continue or engage with a reframe.

### 06 — iOS demonstration

The same inference and intervention experience is demonstrated inside the controlled iOS feed.

---

# Performance & Reliability

Real-time on-device AI introduces practical constraints:

* Model initialization
* Inference latency
* Device hardware
* Battery usage
* Accessibility event frequency
* Scrolling speed
* Overlay positioning
* False positives
* False negatives

The prototype addresses these through:

* Rolling content windows
* Event throttling
* Narrow JSON inference contracts
* Local processing
* Controlled demo content
* Deterministic mock injection

---

# Development Fallback

Live feed analysis is inherently less deterministic than a conventional application demo.

Undoom therefore includes a development-mode content injector that can feed predefined posts into the same detection pipeline.

```text
Mock Content
     ↓
Same Buffer
     ↓
Same AI
     ↓
Same Detection
     ↓
Same Intervention
```

This means the fallback does not use a separate fake UI flow.

It exercises the same application logic used by the live prototype.

---

# Five-Day Prototype

The prototype is structured as a focused five-day build.

| Day | Android                              | iOS / Shared                            |
| --- | ------------------------------------ | --------------------------------------- |
| 1   | Device + Accessibility + Gemini Nano | React Native + detection prompt         |
| 2   | Accessibility → RN bridge            | Buffer + AI integration + mock injector |
| 3   | Live overlay                         | Foundation Models + demo feed           |
| 4   | Performance + reliability            | iOS intervention + integration          |
| 5   | Final testing + live rehearsal       | Demo polish + presentation              |

A recorded Android demonstration is maintained as a fallback for live-demo reliability.

---

# Roadmap

### Detection

* Improved rage-bait classification
* Context-aware toxicity scoring
* Better false-positive handling
* Personalized sensitivity

### Intervention

* Adaptive intervention language
* Reframing strategies
* User-controlled intervention levels
* Behavioral feedback loops

### Platform

* Expanded Android device compatibility
* Additional accessibility integrations
* Expanded iOS in-app experiences
* Performance and battery optimization

### Intelligence

* Multimodal content analysis
* Conversation-level context
* Personalized cognitive interventions
* Long-term behavioral insights stored locally

---

# The Vision

Most systems ask:

> **"Is this content allowed?"**

Undoom asks:

> **"Is this content worth my reaction?"**

That distinction is the foundation of the product.

The long-term vision is a **personal cognitive safety layer** that sits between people and algorithmically optimized feeds.

Not a censorship system.

Not another content filter.

A system designed to help users maintain control over their attention.

---

## Undoom

**Detect the trigger.
Pause the reaction.
Choose what comes next.**

