# End-to-End User Journeys

## 1. First-run journey

Goal: reach the first meaningful solution with minimal friction.

1. Launch.
2. One-sentence value proposition.
3. Sign in/account path.
4. Essential onboarding only.
5. Home.
6. Primary CTA: Scan a Problem.
7. Capture/import.
8. Upload.
9. Semantic progress: Reading → Understanding → Solving → Checking → Explaining.
10. Result screen.
11. Verification state visible immediately.
12. Optional: "Why is this verified?" and "Help me solve it myself."
13. Monetization only after product value is clear.

Failure recovery:
- unreadable image → retake/crop,
- unsupported problem → explicit limitation,
- verification unavailable → solution may be shown as UNVERIFIED,
- temporary outage → retain user input and allow retry.

## 2. Solve journey

1. Capture problem.
2. Problem parser creates semantic representation.
3. User may correct misread expression.
4. Solve job starts.
5. Primary solver executes.
6. Secondary solver executes according to policy.
7. Verification planner selects deterministic checks.
8. Explanation is generated from validated solution state.
9. Result shows final answer, steps, verification status and evidence summary.

Rule: never bury verification status under decorative content.

## 3. "Why am I wrong?" journey

1. User uploads problem and own solution.
2. Problem and attempt are parsed separately.
3. Attempt steps align with reference solution.
4. First meaningful divergence is found.
5. Mistake classification proposes a structured category.
6. Deterministic checks support or reject the diagnosis where possible.
7. User sees exact wrong step, category, concise explanation and one corrective exercise.

Example:
- Your step: `-(x + 2) = -x + 2`
- Correct: `-(x + 2) = -x - 2`
- Category: SIGN_ERROR

## 4. Tutor journey

1. User selects "Help me solve it."
2. Final answer is hidden.
3. Tutor asks the smallest useful next question.
4. Student responds.
5. Attempt is evaluated.
6. Hint ladder escalates only as needed.
7. Completion updates learning evidence.

Invariant: Tutor mode does not silently become answer-dump mode.

## 5. Daily practice journey

1. App opens.
2. Today plan is visible.
3. User taps Continue.
4. Engine selects items using weak skills, spaced repetition, exam weight, recent mistakes and available time.
5. User completes session.
6. Mastery updates.
7. Plan rebalances.
8. Concise session summary appears.

## 6. Exam journey

1. Select exam/curriculum.
2. Set date and target if applicable.
3. Current mastery maps to exam skill weights.
4. Baseline readiness is estimated.
5. Planner schedules study.
6. Mock exams periodically validate readiness.
7. Plan changes based on actual performance.
8. Final review emphasizes high-risk skills and prior mistakes.

## 7. Purchase journey

1. User reaches quota or opens premium feature.
2. Paywall states exact price, billing period, renewal and trial terms.
3. StoreKit purchase occurs.
4. Backend verifies transaction and entitlement.
5. Client refreshes server-authoritative entitlement.
6. Feature unlocks.

## 8. Offline journey

Available offline:
- cached history,
- saved solutions,
- mistake book,
- latest mastery snapshot,
- downloaded content.

Unavailable offline:
- new AI solving,
- deterministic server verification,
- authoritative subscription changes.

## 9. Account deletion journey

1. User requests deletion.
2. Product explains scope.
3. Reauthentication if necessary.
4. Sessions revoked.
5. PII and object assets enter deletion pipeline.
6. Legally/security-required minimized records retained only according to policy.
