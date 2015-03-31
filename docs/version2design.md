# DaoPlayer Version 2 Design

In version 2 the primary change is that the individual songs (`dmo`s) are separate from the overall experience, and that the placement of each `dmo` is made explicit and coherent as a `geotrack`. 

Nominally each song or `dmo` is an Audio Object (or Digital Music Object), which could be used in a range of experiences; the `composition` defines how a set of `dmo`s become `geotrack`s by being situated and tailored for a /specific/ experience. 

Often only a single `dmo`/`geotrack` will be active at any particular moment. The player, based on the `composition`, will handle playing, pausing and stopping `dmo`s, and will also provide appropriate control inputs for active `dmo`s, typically based on sensor inputs.

## `DMO` Interface

From the perspective of a composition a `dmo` would be seen in terms of a public interface including:

- a unique ID;
- metadata, e.g. title, key, time signature, tempo, genre, author, license
- a set of one (v2.0) or more (v2.2) externally visible `sections`s (or states) and their relationships and characteristics;
- a set of zero (v2.0) or more control (v2.1) or input parameters.

A `dmo` might have only a single external `section` (e.g. "the whole song"), even if it has more sub-sections/scenes internally.

A `dmo` may be in one of the following overall states:
- `unprepared`
- `preparing`
- `prepared`
- `playing`
- `ended`

At any moment in time a track may have a current section (and time within that section) and/or a next section (and time of transition).

Each section may have the following public properties:
- ID (unique within this `dmo`) (v2.0)
- metadata, e.g. title, key, time signature, tempo (v2.0)
- minimum duration (v2.0)
- maximum duration (v2.0)
- valid start, i.e. whether you can start with this section (v2.0)
- start cost envelope, i.e. how "bad" it would be to start/fade in this geotrack during this section as a function of time
- start fade duration, i.e. how long to fade in over
- end cost envelope, i.e. how "bad" it would be to end/fade out this geotrack during this section as a function of time
- end fade duration, i.e. how long to fade out over
- pause cost envelope, i.e. how "bad" it would be to pause this geotrack during this section as a function of time
- pause fade duration, i.e. how long to pause/fade over
- pause type: fade and stop, fade and rewind, fade and continue, fade and restart section, fade and restart track
- set of controls (names) which are effective in this section
- set of possible next sections (if any), in each case with:
  - next section ID
  - timing of transition (TBD)
  - cause of transition: automatic (time), automatic (control input), on-request

Controls are defined at the `dmo` level, but may only apply to a subset of `section`s (section controls).

Control is defined as (v2.1):
- TODO

## Composition Model

A composition includes:

- a set of `waypoint`s and a graph of `route`s over those `waypoint`s
- a set of `geotrack`s, each of which is a reference to a `dmo` plus its placement and location-adaptive characteristics defined with reference to the `waypoint`s and `route`s.

Example: single path, single geotrack...

- `path` = set of `route`s
- `route` = pair of `waypoint`s and proximity
- `waypoint` = lat/long and proximity

Start options:

- route (i.e. any point along it) 
- (later) waypoint(s) (selected)
- (later) last active location

Pre-conditions:

- enabled (initially, vs currently...)
- required position accuracy (hysteresis)
- proximity (hysteresis) (to above)
- (later) accuracy of current speed estimate (hysteresis)
- (later) direction/speed along route (hysteresis)

Scripts:

- onactivate
- ondeactivate

In general hystersis can be:

- (relative) on condition
- on time
- (relative) off condition
- off time

E.g. 10m (+/-2@3s) => within 10-2=8m for 3s to start / beyond 10+2=12m for 3s to stop

Other properties:

- priority (of geotrack)
- (later) fadeintime
- (later) fadeouttime

Relationship =~contingencies, e.g.:

- active, active-warning, inactive

Contingency/warning factors, as for activate (minus enabled), but plus:

- track time
- (later) distance along route - starting, esp. ending
- (later) distance along route vs time

Other properties:

- Priority
- (later) min time
- (later) hysteresis

Contingency actions:

- fade
- play pad/volume
- (later) TTS
- (later?) pause
- disable
- (later) rewind / jump to section / reset
- (later) consider also (other contingencies)
- (later) recoveries... (factor & action)

## Default / Background

Perhaps a very-low priority `geotrack`, which may also provide audio/TTS feedback on e.g. GPS problems, nearby `geotracks`.

Perhaps (effectively) a build-in fallback `geotrack` as above.

## DMO Definition / Implementation

Internally, a `dmo` would comprise or be realised as:

- a header (metadata)
- some configuration parameters
- one or more audio `track`s, i.e. audio streams;
- one or more `scene`s, i.e. potentially dynamic configurations of tracks roughly corresponding to distinct states of the `geotrack`.


