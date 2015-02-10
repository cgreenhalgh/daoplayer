# Implementation Nodes

DaoPlayer implementation notes.

## Status

Currently not working so well: seems to handle one mp3 files, but stutters with more being decoded at once, although once decoded it can play/mix at least 3 at once.

Planning to:

- use two background threads to decode in `FileCache`
- including speculative decode in `FileCache` to build up more of a buffer
- more actively manage priorities of decoding in `FileCache`
- try using WAV files!

## Decoding

Each File is read by a `android.media.MediaExtractor`. This supports seeking with `seekTo`, time specified in micro-seconds, and mode (for use with codec) of `SEEK_TO_PREVIOUS_SYNC`. Actually time can be checked via `getSampleTime`. Sample data read with `readSampleData`; unclear if this will literally return only a single sample (testing found ~400 bytes, and combining several in a buffer was slower). Can call `release` when done. On my Samsung Galaxy Nexus (Android 4.3.1 / cyanogenmod) `seekTo` often overshoots. It also seems to return `0` as sample time when it has seeked past the end of the file (should be `-1`). The position resulting from seekTo seems to differ by 1 or 2 samples from the presentation time reported when playing from the start of the file.

Each stream is decoded by a `android.media.MediaCodec`. MP3 doesn't seem to need any particular configuration. Before getting non-consecutive data (e.g. after extractor seek) must call `flush` (which returns all buffers to the codec). Codec will return completed output buffers when it can. I think this is simply dependent on having enough input data (not some concurrent operation). It may be necessary to return the buffer before more data will be returned. If data is not returned and all output buffers are returned then more input data is required, although in general input data can be provided whenever a request for an input buffer succeeds (up to end of stream). On my Samsung Galaxy Nexus (Android 4.3.1 / cyanogenmod) the first decoded block is usually just over half the size of the later blocks, presumably something to do with starting up the codec or internal chunking. The reported presentation times are then out of sync, and appear to reflect times as if the first block had been complete. 

I'm not sure what will happen (e.g. with extracted block size) if WAV format files are used.

Currently audio is decoded to 16 bit signed samples, with the same number of channels as the MP3 file. They are chunked as returned by the codec.

Decoding is done in the background by an `ExecutorService` managed by a `FileCache` instance. Each output frame the `AudioEngine` passes to the `FileCache` the update current/next/future play-out `State`s, and the `FileCache` attempts to manage decoding and cacheing blocks that will be needed soon. The `AudioEngine` asynchronously requests decoded sample blocks from the `FileCache` to play out.

## Mixing and Playing

Output is done by (daoplayer) `AudioEngine`.

`AudioEngine` maintains a queue of past/current/future `State`s. These are marked:

- `DISCARDED` - old, garbage collected
- `WRITTEN` - finished with, e.g. previous current State
- `IN_PROGRESS` - state currently being mixed and/or waiting for blocking write to complete
- `NEXT` - state that will be mixed next, i.e. priority for cacheing audio blocks
- `FUTURE` - state(s) after `NEXT`, e.g. queued from updates or scene changes

Play out is via a single `AudioTrack`, currently fixed as stereo, 16 bit PCM and 44100 Hz. A (daoplayer) `PlayThread`. This sits in a loop calling `fillBuffer`, converting from ints to shorts (via 12 bit right-shift, i.e. divide by 4096) and then blocking write to the `AudioTrack`; each buffer is half the result of `getMinBufferSize`.

`fillBuffer` first checks the `AudioEngine`'s queue of current/next `State`s, and generates current and next states (by advancing track times) if they are not present. It then notifies the `FileCache` of the new state queue, so that it can start to decode and cache audio blocks for the next iteration. 

`fillBuffer` then takes the current State (of Tracks), then iterates over active Tracks and their FileRefs (for decoded files), determines sample (time) extent of FileRef's overlap with target buffer, and walks the File's buffer list (repeatedly if necessary), mixing valid buffer spans into the output buffer.

Note: currently looping to file length is NOT an option, as it requires File length to be determined, which is currently only done by fully decoding the File.

Current Composition, on `setScene` or `updateScene` calls `AudioEngine` `setScene`. This combines the existing `FUTURE` state (if any) with the new/updated scene to determine the new `FUTURE` state, which replaces older `FUTURE` state(s) in the `AudioEngine` state queue.

`setScene` and `updateScene` in turn are called by (daoplayer) `Service`, with update scene called at a scene-defined interval, called on the application thread via `postDelayed`. All JavaScript evaluation is performed in `setScene`/`updateScene`; static values are passed to `setScene`.

## Thread summary

- decode - ExecutorService (current single thread) managed by `FileCache`, started by `AudioEngine` `start`
- play - single PlayThread, updates and uses current `AudioEngine` `State` queue to (a) notify `FileCache` of upcoming requests and (b) fill a fresh (half) buffer at a time and blocking play it
- setScene / updateScene - single application thread, from UI event, postDelayed or location update


