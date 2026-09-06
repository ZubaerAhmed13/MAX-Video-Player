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
- A unit test explicitly exercises values larger than `Int.MAX_VALUE`.

## Physical certification
- 3 GB+ physical playback: **NOT VERIFIED** until a real 3 GB+ asset is played on a device/emulator environment with realistic storage/provider behavior.
- Very-long-duration seek certification: **NOT VERIFIED**.

These remain requirements and are not downgraded.
