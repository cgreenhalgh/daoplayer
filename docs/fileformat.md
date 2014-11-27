# File Format

## Format

version 1

JSON object with:
- `meta`, `tracks` and `scenes` - see below
- `defaultScene` - name of default/initial scene (if any)
- `constants` - optional set of Javascript constants for the whole composition - see below

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
- `trackPos` - start position in track, in seconds (float, default 0)
- `filePos` - start position in file, in second (float, default 0)
- `length` - length of file to play (float, default -1 = all)
- `repeats` - how many times to repeat (integer, default 1, -1 = forever)

### `scenes`

Array of scene objects with:
- `name` - name (string)
- `partial` - scene is partial, i.e. unspecified track stay at current levels (boolean, default false)
- `tracks` - array of track refs (see below)
- `constants` - optional set of Javascript constants for the whole composition - see below
- `onload` - Javascript code to execute (after onany) when this scene is loaded (string, see dynamic scenes, below).
- `onupdate` - Javascript code to execute (after onany) when this scene is updated, e.g. when time or position changes (string, see dynamic scenes, below).
- `updateInterval` - 

Track ref is object with:
- `name` - name of track
- `volume` - volume to play (float or string, 1.0 is "full", default unchanged). 
- `pos` - play position in track, in seconds (float, unspecified => "current")

### `constants`

Map (i.e. name-value pairs) of Javascript constants to define before any `onload`, `onupdate` or dynamic volume script is executed. 

For example, `"constants":{"a":0.5,"b":"function(x){return 1-x;}"}`

## Dynamic scenes

If the volume of a track ref is a string then it is evaluated as a Javascript expression.

Any dynamic (i.e. Javascript) track ref volumes are evaluated when the scene is loaded, and also each time the scene is "updated", i.e. if the user's position changes or the scene timer goes off.

Any constants at the whole composition or current scene level are defined before any other scripts are executed.

If the scene has Javascript code specified in `onload` or `onupdate` then this is executed first, as appropriate (i.e. `onload` if the scene is being loaded and `onupdate` if it has already been loaded and is being updated).

### Standard variables

`window.position`: last known user position (WGS-84 coordinates, i.e. GPS). Nulll or undefined if there has been no position reported since the app started. Value is an object with fields:
- `lat`: latitude, degrees (float)
- `lng`: longitude, degrees (float)

### Standard functions

`window.distance(lat1,lng1,lat2,lng2)`: distance in metres from WGS-84 (GPS) position (`lat1`,`lng1`) and (`lat2`,`lng2`).

