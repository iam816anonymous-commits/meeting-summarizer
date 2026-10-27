# Skill Manifest Schema

Skill manifests are versioned metadata for internal capabilities. They do not grant operating-system permissions.

## Required fields
- `schemaVersion`: integer, initially 1
- `id`: stable lowercase identifier, e.g. `speech-to-text`
- `displayName`: user/developer-readable name
- `version`: semantic version
- `description`: bounded purpose
- `inputSchema` and `outputSchema`: typed payload contracts
- `permissions`: declared capabilities required
- `networkPolicy`: `never`, `optional-consent`, or `required`
- `resourceProfile`: estimated memory/CPU/storage class
- `timeoutMs`: bounded execution timeout
- `cancellable`: boolean
- `sideEffects`: explicit list
- `failureModes`: structured error codes
- `tests`: required conformance tests

## Validation rules
- Unknown schema versions fail closed.
- IDs are unique and immutable after release.
- A manifest cannot self-grant permissions or change user consent.
- Network-enabled skills must be visibly disclosed and require a policy gate.
- A skill requesting microphone access is rejected unless it is the designated meeting-capture implementation.
- No manifest field can authorize secret recording, auto-start on boot, or unreviewed sharing.
- Validate all manifests at startup and test malformed/unsupported manifests.
