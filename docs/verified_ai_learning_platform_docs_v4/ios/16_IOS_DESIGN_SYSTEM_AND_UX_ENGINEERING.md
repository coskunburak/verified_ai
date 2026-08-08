# iOS Design System and UX Engineering

## Quality target

The app must feel like a premium native learning product, not an AI prototype.

Quality dimensions:
- predictable navigation,
- excellent perceived latency,
- readable mathematical hierarchy,
- restrained motion,
- accessibility,
- trust-oriented verification design.

## Design tokens

Centralize:
- semantic colors,
- typography roles,
- spacing scale,
- radii,
- borders,
- motion durations/easing,
- haptic semantics.

No random per-view constants where a design token exists.

## Verification UI

### VERIFIED
Positive but not celebratory overclaim. Badge + details.

### PARTIALLY_VERIFIED
Clearly state what was verified and what remains uncertain.

### UNVERIFIED
Neutral-warning semantics: "A solution is available, but we could not independently verify it."

Never communicate status using color alone.

## Solve progress

Use semantic stages: Reading → Understanding → Solving → Checking → Explaining.

Avoid fake precise progress percentages unless they represent real stages.

## Math presentation

Requirements:
- scalable equations,
- long-expression handling,
- selectable/copy behavior when safe,
- step spacing,
- accessible description.

Math rendering choice must be benchmarked for performance, accessibility and notation coverage.

## Camera UX

- framing guides,
- auto-detection with manual override,
- blur/glare feedback,
- retake fast path,
- multi-question selection,
- crop before upload.

Sprint 4.1 implementation status:
- implemented source selection for camera, Photos picker, and Files/PDF import;
- implemented camera permission/recovery states, AVFoundation preview, framing guide, shutter, retake, replace, and local accept handoff;
- implemented client-side blur/glare/framing warnings and manual crop metadata before upload;
- implemented PDF first-page preview for local review;
- deferred automatic edge detection, perspective correction, multi-question splitting, and backend upload to later Phase 4 sprints;
- real-device camera/focus/picker validation is tracked as `TD-CAPTURE-001`.

## Tutor UX

Tutor is goal-directed, not merely a chat transcript. One useful question at a time, visible hint ladder and progress.

## Home dashboard

Prioritize:
- Continue Today's Practice,
- Scan a Problem,
- exam countdown if relevant,
- highest-priority weakness,
- concise progress.

Do not create a crowded analytics dashboard as Home.

## Accessibility

- Dynamic Type,
- VoiceOver,
- high contrast,
- Reduced Motion,
- large tap targets,
- equation accessibility labels,
- state never conveyed only through animation.

## Errors

Classify and recover:
- unreadable image,
- unsupported problem,
- network issue,
- provider busy,
- verification unavailable,
- entitlement required.

Every recoverable error has a concrete next action.

## Paywall

Show exact price, billing period, trial terms, renewal, restore and legal links. No deceptive scarcity or hidden close behavior.
