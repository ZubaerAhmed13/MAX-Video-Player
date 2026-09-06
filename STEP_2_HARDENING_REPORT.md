# MAX VIDEO PLAYER — STEP 2 POST-CERTIFICATION HARDENING REPORT

## 1. Scope

This report records three Step-2 defects found after the original Step-2 certification and the corrective work applied before beginning Step 3.

Repository: `ZubaerAhmed13/MAX-Video-Player`

Base `main` before hardening:

`c577d4478fb5e6e875535a194c9dc1ce507b7e54`

Hardening branch:

`fix/step-2-post-certification-hardening`

Step 3: **NOT STARTED**

Physical-device certification: **NOT VERIFIED — DEFERRED TO STEP 10**

## 2. Issues addressed

| Issue | Prior status | Hardening result |
|---|---|---|
| Rename → rescan stable-ID preservation | Needs fix | PASS |
| Android API 23–28 thumbnails | Needs fix | PASS |
| SAF failure availability consistency | Needs fix | PASS |

## 3. Rename → rescan stable-ID preservation

### Root cause

The immediate rename path already updated the indexed media row through `relinkMedia()` while retaining the historical app stable ID. However, a later MediaStore or SAF rescan could recompute a fallback stable ID from renamed metadata such as the display name and then insert that newly computed identity.

That could split one real media item into two app identities and disconnect long-lived relationships such as favourites, playlists and playback history from the rescanned row.

### Fix

A source-local scan reconciliation layer now compares freshly scanned rows with existing indexed rows by provider URI before the new scan is committed.

When the provider URI is already known, the historical app stable ID is reused even when the scanner produced a different fallback ID after rename.

The global Step-1 fallback-ID algorithm was deliberately left unchanged to avoid an unnecessary identity migration across existing libraries.

### Coverage

`LibraryScanHardeningTest` verifies:

- rename → rescan with the same provider URI keeps the historical stable ID;
- a historical stable ID is preferred over a transient newly generated fallback ID for the same URI;
- renamed metadata still refreshes while identity remains stable.

## 4. Android API 23–28 thumbnails

### Root cause

The Step-2 thumbnail repository used `ContentResolver.loadThumbnail`, available on API 29+, and intentionally returned a placeholder/null result below API 29. That left Android API 23–28 without real local-video thumbnail extraction.

### Fix

The thumbnail pipeline is now API-aware:

- API 29+ → `ContentResolver.loadThumbnail`;
- API 27–28 → `MediaMetadataRetriever.getScaledFrameAtTime`;
- API 23–26 → `MediaMetadataRetriever.getFrameAtTime` followed by bounded, aspect-ratio-preserving downscale when required.

Existing safeguards remain:

- thumbnail work on `Dispatchers.IO`;
- bounded request dimensions;
- 16 MiB bounded LRU memory cache;
- cancellation checks;
- graceful placeholder/null result on invalid, missing or provider-failed media;
- no full-video loading into memory.

### Coverage

`ThumbnailCompatibilityPolicyTest` verifies the selected implementation strategy for APIs 23–36.

CI additionally runs `ThumbnailRepositoryInstrumentedTest` on:

- API 26 — PASS;
- API 28 — PASS;
- the regular API-35 full instrumentation suite — PASS.

The API-26 emulator executes the API 23–26 legacy-retriever branch family, and the API-28 emulator executes the API 27–28 scaled-retriever branch.

## 5. SAF failure availability consistency

### Root cause

Permission failures correctly changed the source status to `PERMISSION_LOST` and marked existing indexed media unavailable. Generic SAF provider failures changed the source status to `UNAVAILABLE` but could leave the previously indexed media rows still marked available before the failure was rethrown.

### Fix

All real SAF scan failures now update source state and indexed-media availability together inside a Room transaction:

- `SecurityException` → source `PERMISSION_LOST`, indexed rows `UNAVAILABLE`, no rethrow;
- other provider/scan exceptions → source `UNAVAILABLE`, indexed rows `UNAVAILABLE`, then the original failure is surfaced;
- `CancellationException` is rethrown immediately and is not misclassified as a storage/source failure.

This keeps the UI and persisted source model consistent after failed scans.

### Coverage

`LibraryScanHardeningTest` verifies the permission-loss and generic-provider-failure dispositions.

## 6. Files changed by the hardening work

Production changes:

- `app/src/main/java/com/zubaer/maxvideoplayer/feature/library/LibraryRepository.kt`
- `app/src/main/java/com/zubaer/maxvideoplayer/feature/library/LibraryScanHardening.kt`
- `app/src/main/java/com/zubaer/maxvideoplayer/feature/library/ThumbnailRepository.kt`
- `app/src/main/java/com/zubaer/maxvideoplayer/feature/library/ThumbnailCompatibilityPolicy.kt`

Tests:

- `app/src/test/java/com/zubaer/maxvideoplayer/feature/library/LibraryScanHardeningTest.kt`
- `app/src/test/java/com/zubaer/maxvideoplayer/feature/library/ThumbnailCompatibilityPolicyTest.kt`

CI/documentation:

- `.github/workflows/android-ci.yml`
- `PARITY_MATRIX.md`
- `STEP_2_HARDENING_REPORT.md`

No Step-1 or Step-2 feature was deleted or disabled to implement these fixes.

## 7. Automated verification

Authoritative code hardening run before this report commit:

- Workflow: `Android CI`
- Run: `34054268096` (#73)
- SHA: `7d2170f4003f7f6a1b60b843583bac623395e730`

Results:

- debug build + JVM tests — **PASS**
- release compilation — **PASS**
- lint — **PASS**
- full API-35 instrumentation — **PASS**
- API-26 targeted legacy-thumbnail instrumentation — **PASS**
- API-28 targeted legacy-thumbnail instrumentation — **PASS**

The final documentation head must pass the same enabled CI jobs before PR merge. This report does not pre-claim that later run.

## 8. Remaining physical boundary

Still **NOT VERIFIED — DEFERRED TO STEP 10**:

- real 3 GB+ / 5 GB+ / 10 GB+ playback;
- real 4K / HDR playback;
- SD card and USB/OTG behavior;
- OEM-specific MediaStore/document-provider behavior;
- physical Bluetooth/headset behavior;
- battery, thermal and long-run testing;
- broad phone/tablet device matrix.

These physical checks are not required to validate the three software defects corrected here and are not falsely claimed as complete.

## 9. Result

All three reported Step-2 software defects have corrective implementations and green automated evidence on the hardening code head. The final docs head will be merged only after its CI gate also passes.

# STEP 2 HARDENING: PASS
