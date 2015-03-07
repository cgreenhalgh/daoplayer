# File Format

## Format

version 1

JSON object with:
- `meta`, `tracks`, `scenes`, `context` - see below
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
- `sections` - array of sections (see below)
- `unitTime` - duration in sections of a "unit" of the song when dynamically choosing sections. Usually one bar, but may be one beat or a small number of beats or bars. Defaults to shortest section length.

File ref is Object with:
- `path` - file path (string)
- `trackPos` - start position in track, in seconds (float, default 0)
- `filePos` - start position in file, in second (float, default 0)
- `length` - length of file to play (float, default -1 = all)
- `repeats` - how many times to repeat (integer, default 1, -1 = forever; NB cannot repeat a file with length -1/unspecified)

`section` is Object with:
- `name` - name (string)
- `trackPos` - start position in track, in seconds (float, default 0)
- `length` - length of section (float, default is to start of next section, else -1 = end of track)
- `startCost` - "cost" of starting with this section (float, default very large, but smaller for first section)
- `endCost` - "cost" of ending during this section (float, default very large, but smaller for last section)
- `next` - array of Next Sections (see below)

Next section is Object with:
- `name` - name of next section
- `cost` - "cost" of this being the next section (float, default 0)

The next section cost for unspecified sections is assumed to be very large (infinite?), but smaller for the next section(!).

### `scenes`

Array of scene objects with:
- `name` - name (string)
- `partial` - scene is partial, i.e. unspecified track stay at current levels (boolean, default false)
- `tracks` - array of track refs (see below)
- `constants` - optional set of Javascript constants for the whole composition - see below
- `vars` - optional set of Javascript variables, reinitialised on each update and shared by all script running on that update (e.g. onupdate, dynamic volume & position) - see below
- `onload` - Javascript code to execute (after onany) when this scene is loaded (string, see dynamic scenes, below).
- `onupdate` - Javascript code to execute (after onany) when this scene is updated, e.g. when time or position changes (string, see dynamic scenes, below).
- `updatePeriod` - regular period in seconds after which the scene should be updated (float, default undefined => never, see dynamic scenes, below). E.g. `3.0` implies update the scene every 3 seconds.
- `waypoints` - map (object) from scene-specific waypoint name to global waypoint name or alias (as used in `context` waypoint list)

Track ref is object with:
- `name` - name of track
- `volume` - volume to play (float or string, 1.0 is "full", default unchanged). 
- `pos` - play position in track, in seconds (float, unspecified => "current"), or (planned!) function returning an array of alternating `sceneTime`,`trackPos` values.
- `prepare` - audio engine should prepare to play track, even if volume is currently 0 (boolean, default false) (Note: currently not used)
- `update` - whether to re-evaluate `volume` and `pos` on each scene update (boolean, default true)

### `context`

Object with:
- `waypoints` - array of waypoints (see below)
- `routes` - array of routes (see below)

Waypoint (todo) is object with:
- `name` - name of waypoint (global name)
- `aliases` - array of alternative names for waypoint (e.g. 'start')
- `lat` - latitude, degrees (float)
- `lng` - longitude, degrees (float)
- `nearDistance` - how far from waypoint is 'near', metres (float, default provisionally 15m, may change)
(future: polyline or other geometry)

Route (todo) is object with
- `from` - name of origin waypoint
- `to` - name of destination waypoint
- `nearDistance` - how far off the direct route is 'near' to it, metres (float, default provisionally 5m, may change)
(future: `oneway` - one way flag (boolean, default false, i.e. two-way))
(future: `via` - array of intermediate waypoint name(s), which are along the route but there you cannot join the route)

### `constants` and `vars`

Map (i.e. name-value pairs) of Javascript constants to define before any `onload`, `onupdate` or dynamic volume script is executed. 

For example, `"constants":{"a":0.5,"b":"function(x){return 1-x;}","c","{lat:52.953685,lng:-1.188429}"}`

## Dynamic scenes

If the volume of a track ref is a string then it is evaluated as a Javascript expression.

Any dynamic (i.e. Javascript) track ref volumes are evaluated when the scene is loaded, and also each time the scene is "updated", i.e. if the user's position changes or the scene timer goes off.

Any constants at the whole composition or current scene level are defined before any other scripts are executed. Any scene variables are defined (with their initial values) before any other scripts are executed.

If the scene has Javascript code specified in `onload` or `onupdate` then this is executed first, as appropriate (i.e. `onload` if the scene is being loaded and `onupdate` if it has already been loaded and is being updated).

If a volume function returns an array then this is assumed to define a piece-wise linear-interpolated function of `sceneTime` (in seconds). For example, a smooth fade in over three seconds at the start of the scene would be `[0,0,3.0,1.0]`. A return value of `null` implies unchanged.

If the `pos` of a track ref is a String then it is evaluated as a javascript expression. Expected return should be an array of alternating `sceneTime`,`trackPos` (time) values. As each scene time is reached the track position jumps to the specified track time.
 A return value of `null` implies unchanged.

Note that volume and pos functions are always called on scene load, but only called on scene update if the scene track ref property `update` is not false. 

### Standard variables

`position`: last known user position (WGS-84 coordinates, i.e. GPS). Nulll or undefined if there has been no position reported since the app started. Value is an object with fields:
- `lat`: latitude, degrees (float)
- `lng`: longitude, degrees (float)
- `age`: age of position, i.e. time since it was received, in seconds (float)
- `accuracy`: estimated accuracy of position (float, metres)

`sceneTime`: time in seconds since this scene was loaded (float).

`totalTime`: time in seconds since this composition was started (float).

`trackTime`: time in seconds of current playout point within track (float). This is currently a slightly conservative estimate (i.e. part of a second into the future), and is only available to code within the context of a track ref, i.e. a dynamic volume or dynamic track position expression. 

`trackVolume`: volume of current track at the current playout point (float). Only available to code within the context of a dynamic volume function. 

`currentSection`: currently playing section of current track; only available within a dynamic track position expression. Null if no current section, else an Object with fields:
- `name`: name of current section (string)
- `startTime`: track time in seconds when current section started (float)
- `endTime`: track time in seconds when current section will end (float), if the section has a length/end time

`activity`: one of `NOGPS`, `STATIONARY`, `WALKING` (future?: `RUNNING`, `FASTD`)

`waypoints` (todo): map from local name of waypoint to objects, each with:
- `name` - global name of waypoint -->
- `lat`, `lng` of waypoint - see waypoint
- `distance` - direct distance from waypoint, metres (float)
- `near` - near to waypoint? (boolean), i.e. accordinng to waypoint `nearDistance`
(future: `routeDistance`, `routeNear`)
- `route` (todo) - array of intermediate waypoint local names on shortest path to waypoint
- `timeAtCurrentSpeed` - time (seconds) to waypoint along route at current speed
- `timeAtWalkingSpeed` - time (seconds) to waypoint along route at typical speed

and in the last waypoint only: 
- `nearTime` - time while (still) near, 
- `notNearTime` - since waypoint was near

`lastWaypoint` (todo): if not null then name of the "last" visited waypoint, as set with `setLastWaypoint`

`nextWaypoint` (todo): name of closest/closest to route waypoint, which will be in `waypoints`

`currentSpeed`: speed, metres/second, of current movement

`walkingSpeed`: speed, metres/second, of estimated 'normal' walking pace for current user

### Standard functions

`distance(coord1,coord2)`: distance in metres from WGS-84 (GPS) position `coord1` (`{"lat":...,"lng":...}`) and `coord2`. If `coord2` is not defined then the most recent `daoplayer.position` is used as the reference. Returns `null` if `coord1` or `coord2` are undefined.

`daoplayer.setScene(name)`: load the specified scene.

`daoplayer.setLastWaypoint(name)`: make the named waypoint the 'last' waypoint (`lastWayout`) for the next update. To resolve waypoint name it tries local names first, then global names, then global aliases.

`daoplayer.log(message)`: diagnostic output to player log view.

`daoplayer.speak(text,flush)`: speak text using text to speech engine (if enabled).

(future? - `daoplayer.status(message,description,type)` (todo): update single-line status view, `type` is `info`, `warning` or `error`.)

`pwl(in, [in1,out1,in2,out2,...], default)`: piece-wise linear interpolation `in` to `out`, i.e. if `in` is less than `in1` then `out1`; if `in` is between `in1` and `in2` then a proportional value between `out1` and `out2`; ...; if `in` is greater than the last in value then last out value; etc. For example, to convert a distance in metres to a volume such that volume is 1 (full) up to 10 metres, then drops off linearly to 0 at 30 metres or more, use `pwl(distance,[10,1,30,0])`. (optional) `default` is returned if `in` is undefined or null.

