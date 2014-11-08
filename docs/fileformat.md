# File Format

version 1

JSON object with:
- `meta`, `tracks` and `scenes` - see below
- `defaultScene` - name of default/initial scene (if any)

## `meta`

Object with metadata:
- `mimetype` - `application/x-daoplayer-composition`
- `version` - `1`

## `tracks`

Array of track Objects with:
- `name` - name (string)
- `pauseIfSilent` - (boolean, default false)
- `files` - array of file refs (see below)

File ref is Object with:
- `path` - file path (string)
- `trackPos` - start position in track, in samples (integer, default 0)
- `filePos` - start position in file, in samples (integer, default 0)
- `length` - length of file to play (integer, default -1 = all)
- `repeats` - how many times to repeat (integer, default 1, -1 = forever)

## `scenes`

Array of scene objects with:
- `name` - name (string)
- `partial` - scene is partial, i.e. unspecified track stay at current levels (boolean, default false)
- `tracks` - array of track refs (see below)

Track ref is object with:
- `name` - name of track
- `volume` - volume to play (float, 1.0 is "full", default unchanged)
- `pos` - play position in track, in samples (integer, unspecified => "current")


