# File Format

## Format

version 1

JSON object with:
- `meta`, `tracks` and `scenes` - see below
- `defaultScene` - name of default/initial scene (if any)

### `meta`

Object with metadata:
- `mimetype` - `application/x-daoplayer-composition`
- `version` - `1`

### `tracks`

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

### `scenes`

Array of scene objects with:
- `name` - name (string)
- `partial` - scene is partial, i.e. unspecified track stay at current levels (boolean, default false)
- `tracks` - array of track refs (see below)
- `onany` - Javascript code to execute (first) whenever this scene is loaded or updated (string, see dynamic scenes, below).
- `onload` - Javascript code to execute (after onany) when this scene is loaded (string, see dynamic scenes, below).
- `onupdate` - Javascript code to execute (after onany) when this scene is updated, e.g. when time or position changes (string, see dynamic scenes, below).

Track ref is object with:
- `name` - name of track
- `volume` - volume to play (float or string, 1.0 is "full", default unchanged). 
- `pos` - play position in track, in samples (integer, unspecified => "current")

### Dynamic scenes

If the volume of a track ref is a string then it is evaluated as a Javascript expression.

Any dynamic (i.e. Javascript) track ref volumes are evaluated when the scene is loaded, and also each time the scene is "updated", i.e. if the user's position changes or the scene timer goes off.

If the scene has Javascript code specified in `onany`, `onload` or `onupdate` then this is executed first, as appropriate (i.e. `onany` and `onload` if the scene is being loaded and `onany` and `onupdate` if it has already been loaded and is being updated).

