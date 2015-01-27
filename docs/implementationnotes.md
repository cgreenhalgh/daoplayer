# Implementation Nodes

DaoPlayer implementation notes.

## Decoding

Each File is read by a `android.media.MediaExtractor`. This supports seeking with `seekTo`, time specified in micro-seconds, and mode (for use with codec) of `SEEK_TO_PREVIOUS_SYNC`. Actually time can be checked via `getSampleTime`. Sample data read with `readSampleData`; unclear if this will literally return only a single sample (testing found ~400 bytes, and combining several in a buffer was slower). Can call `release` when done.

Each stream is decoded by a `android.media.MediaCodec`. MP3 doesn't seem to need any particular configuration. Before getting non-consecutive data (e.g. after extractor seek) must call `flush` (which returns all buffers to the codec). Codec will return completed output buffers when it can. I think this is simply dependent on having enough input data (not some concurrent operation). It may be necessary to return the buffer before more data will be returned. If data is not returned and all output buffers are returned then more input data is required, although in general input data can be provided whenever a request for an input buffer succeeds (up to end of stream).

Currently audio is decoded to 16 bit signed samples, with the same number of channels as the MP3 file. They are chunked as returned by the codec.

Decoding is done in the background by an `AsyncTask`. Each one is executed immediately. The complete MP3 is decoded before playback of that file commences.

## Mixing and Playing

Output is done by (daoplayer) `AudioEngine`.

Play out is via a single `AudioTrack`, currently fixed as stereo, 16 bit PCM and 44100 Hz. A (daoplayer) `PlayThread`. This sits in a loop calling `fillBuffer`, converting from ints to shorts (via 12 bit right-shift, i.e. divide by 4096) and then blocking write to the `AudioTrack`; each buffer is half the result of `getMinBufferSize`.

`fillBuffer` gets current State (of Tracks), then iterates over active Tracks and their FileRefs (for decoded files), determines sample (time) extent of FileRef's overlap with target buffer, and walks the File's buffer list (repeatedly if necessary), mixing valid buffer spans into the output buffer.

Note: currently looping to file length is an option, and requires File length to be determined, which is currently only done by fully decoding the File.

Current Composition, on `setScene` or `updateScene` calls `AudioEngine` `setScene`. These in turn are called by (daoplayer) `Service`, with update scene called at a scene-defined interval, called on the application thread via `postDelayed`. All JavaScript evaluation is performed in `setScene`/`updateScene`; static values are passed to `setScene`.

`AudioFile` `queueDecode` is called by `AudioEngine` `addFile` which is called in turn by `Composition` `read`.

## Thread summary

- decode - AsyncTask per File, started by AudioEngine addFile
- play - single PlayThread, uses current AudioEngine State to fill a fresh (half) buffer at a time and blocking play
- setScene / updateScene - single application thread, from UI event, postDelayed or location update


