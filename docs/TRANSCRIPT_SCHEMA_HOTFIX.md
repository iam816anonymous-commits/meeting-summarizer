# SQLite transcript schema hotfix

## Root cause
Database version 2 declared `is_edited` in the version-1-to-version-2 upgrade, but the fresh-database `onCreate` schema omitted it. A database created directly at version 2 therefore lacked the column used by retry cleanup (`DELETE FROM transcript_segments WHERE session_id = ? AND is_edited = 0`).

## Fix
- Create fresh transcript tables with `is_edited INTEGER NOT NULL DEFAULT 0`.
- Bump the database version to 3 so already-installed version-2 databases run an upgrade.
- Make the upgrade inspect `PRAGMA table_info(transcript_segments)` and add the column only if it is missing. This is safe for both version-1 databases and version-2 databases where the column already exists.
- Keep existing transcript/job rows; no database reset or app-data deletion is required.

## Verification
CI must pass unit tests, Android lint, debug APK build and APK verification. On-device regression: open an existing meeting, retry transcription, confirm the retry completes without the SQLite error, and confirm corrected segments remain unchanged.
