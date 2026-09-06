# Step 1 Large-Media / Integer-Safety Audit

## Target
Architecture target: multi-GB media including 3 GB+ and long-duration content.

## Static findings
- Source files are referenced by `Uri`; playback does not copy source media.
- No playback path reads an entire media file into RAM.
- Metadata uses MediaMetadataRetriever/MediaExtractor against URI sources.
- File sizes, duration, position, history timestamps, and sampled offsets use Kotlin `Long`.
- Sample fingerprinting is bounded to at most three 1 MiB regions plus length metadata.
- Sample offsets use `Long` channel positioning.
- UI seek uses a normalized float only for slider presentation; the actual target position is computed back to a `Long`. Source/storage arithmetic does not use `Int` file sizes.
- Unit/instrumentation coverage exercises values larger than `Int.MAX_VALUE`.
- Room instrumentation persists and reads a `3_500_000_000L` size value without truncation.

## Automated verification
Authoritative verified code SHA: `ebf58eb0a95ac6e59429417d990caac212ace58e`  
Android CI run: `34032102391`

- Debug/JVM unit gate: **PASS**
- Release compilation: **PASS**
- Lint: **PASS**
- API-35 instrumentation: **PASS**
- Long-safe Room persistence evidence: **PASS**
- Real local URI/file playback with a compact deterministic H.264 fixture: **PASS**

The compact fixture verifies the real playback path and transport behavior; it is not evidence that a 3 GB file itself was physically played.

## Physical certification
- 3 GB+ physical playback: **NOT VERIFIED** until a real 3 GB+ asset is played on a representative device/environment with realistic storage/provider behavior.
- Very-long-duration seek certification: **NOT VERIFIED**.
- 3840×2160/4K physical playback: **NOT VERIFIED**.

These remain requirements and are not downgraded or inferred from architecture alone.
