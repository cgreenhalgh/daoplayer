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
- `updatePeriod` - regular period in seconds after which the scene should be updated (float, default undefined => never, see dynamic scenes, below). E.g. `3.0` implies update the scene every 3 seconds.

Track ref is object with:
- `name` - name of track
- `volume` - volume to play (float or string, 1.0 is "full", default unchanged). 
- `pos` - play position in track, in seconds (float, unspecified => "current")

### `constants`

Map (i.e. name-value pairs) of Javascript constants to define before any `onload`, `onupdate` or dynamic volume script is executed. 

For example, `"constants":{"a":0.5,"b":"function(x){return 1-x;}","c","{lat:52.953685,lng:-1.188429}"}`

## Dynamic scenes

If the volume of a track ref is a string then it is evaluated as a Javascript expression.

Any dynamic (i.e. Javascript) track ref volumes are evaluated when the scene is loaded, and also each time the scene is "updated", i.e. if the user's position changes or the scene timer goes off.

Any constants at the whole composition or current scene level are defined before any other scripts are executed.

If the scene has Javascript code specified in `onload` or `onupdate` then this is executed first, as appropriate (i.e. `onload` if the scene is being loaded and `onupdate` if it has already been loaded and is being updated).

### Standard variables

`position`: last known user position (WGS-84 coordinates, i.e. GPS). Nulll or undefined if there has been no position reported since the app started. Value is an object with fields:
- `lat`: latitude, degrees (float)
- `lng`: longitude, degrees (float)
- `age`: age of position, i.e. time since it was received, in seconds (float)
- `accuracy`: estimated accuracy of position (float, metres)

`sceneTime`: time in seconds since this scene was loaded (float).

`totalTime`: time in seconds since this composition was started (float).

### Standard functions

`distance(coord1,coord2)`: distance in metres from WGS-84 (GPS) position `coord1` (`{"lat":...,"lng":...}`) and `coord2`. If `coord2` is not defined then the most recent `daoplayer.position` is used as the reference. Returns `null` if `coord1` or `coord2` are undefined.

`daoplayer.setScene(name)`: load the specified scene.

`daoplayer.log(message)`: diagnostic output to player log view.

`pwl(in, [in1,out1,in2,out2,...], default)`: piece-wise linear interpolation `in` to `out`, i.e. if `in` is less than `in1` then `out1`; if `in` is between `in1` and `in2` then a proportional value between `out1` and `out2`; ...; if `in` is greater than the last in value then last out value; etc. For example, to convert a distance in metres to a volume such that volume is 1 (full) up to 10 metres, then drops off linearly to 0 at 30 metres or more, use `pwl(distance,[10,1,30,0])`. (optional) `default` is returned if `in` is undefined or null.

