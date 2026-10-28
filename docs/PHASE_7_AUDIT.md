# Phase 7 audit — personal profile and action-item review

## Scope
- Add a user-editable preferred name, aliases, and optional work-context keywords.
- Rank transcript findings into possible direct assignments, unverified first-person commitments, generic “you” assignments, name mentions, and work-context matches.
- Make review state explicit: needs review, confirmed, or rejected. The user can reset a decision.
- Improve offline English model installation progress with an animated spinner, determinate download progress when available, and separate download/install/verify stages.
- Clarify the completed recording banner as “Recording stopped and saved.”

## Privacy and attribution
- Profile fields and review decisions use app-private SharedPreferences on the device.
- No profile or transcript is sent to a network service.
- A configured name is a search hint, not proof of identity.
- First-person “I/we will” is not assigned to the user because speaker identity is not established.
- Generic “you” language remains uncertain. A name mention without assignment wording is not classified as a direct assignment.
- This is a conservative rule-based ranking feature; false positives and false negatives are expected and review is required.

## Tests
- Classifier unit tests cover explicit named assignment wording, bare name mentions, generic “you”, unverified first-person commitments, unrelated statements, and work-context matches.
- Existing Phase 6 evidence-linked summary tests remain in place.

## Validation status
CI must pass unit tests, APK assembly, Android lint, APK existence checks, and artifact upload before merge.
Physical-device validation remains pending for model download progress, saved-recording feedback, profile persistence, and review-state persistence.
