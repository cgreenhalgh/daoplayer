# Audio Subsystem Design Notes

Based on Adrian Hazzard's audio sculpture trail and related audio design notes.

Main entities:

- `File` - an audio file, the basic unit of audio
- `Track` - the most basic unit of playback, defined by a sequence of references to (parts of) Files
- `Group` - a set of Tracks which can be synchronised and controlled together
- `Scene` - a (perhaps partial) playback state, i.e. which Tracks/Group are playing, at what point and at what volume

## File

A single audio file. The basic unit of audio definition.

Initially probably has to be mp3, stereo or mono, at 44100Hz

Not played directly - see Track. (Many Tracks can include the same File.)

## Track

The most basic unit of playback, defined by a sequence of references to Files.

Has a nominal play-head position, marking audio committed to play (not necessarily what is heard now).

Can be playing, paused or stopped.

A track can be specified to automatically pause if silent (or to continue advancing the play-head position).

The track itself does not loop.

Has a single volume which can be controlled by a scene.

A reference to a File can be looped, i.e. file is virtually included repeatedly. 

A reference can be to only part of a file.

A reference to a File can specify a fixed fade-in and/or fade-out (?).

It *might* be possible to change a Track while it is playing, although adding new tracks to a Group and changing Scene should give equivalent functionality. 

## Group

A set of Tracks which can be synchronised and controlled together.

Can be controlled like a Track: 
- Can be playing, paused or stopped.
- Can be specified to automatically pause if silent (or to continue advancing the play-head -position).
- Does not loop.
- Has a single volume which can be controlled by a scene.

Can optionally synchronise the Tracks within it. In the basic case this means that the play-back position of each Track is the same and is the same as the play-back position of the Group. 

In an unsynchronized group the play-back positions of Tracks are unconstrained.

It should be possible to add new Tracks to an existing Group. 

It shoudl be possible to remove or GC Tracks from a Group, but perhaps only when they are silent or stopped.

## Scene

A playback state, i.e. which Tracks/Group are playing, at what point and at what volume.

May be partial, i.e. specify state only for some Tracks/Groups, and/or only some aspects of Tracks/Groups (e.g. volume but not play-back position).

Nominally for each Track and Group a scene specifies:
- a play-back position (often either 0=from start or unspecified=from current position)
- an overall volume (perhaps a volume per channel or volume plus pan if not mono)

Scenes can be:
- enacted immediately
- queued for enactment after a specific delay (e.g. to create a fade)
- queued for enactment at a certain Track/Group play-back position (e.g. to synchronize with the music)

A complete (non-partial) state would imply that all other existing Tracks/Group were silenced. This might also pause them depending the Track/Group configuration.

