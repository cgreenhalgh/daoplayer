# DaoPlayer Version 2 Design

In version 2 the primary change is that the individual songs ("geo-tracks") are separate from the overall experience. 

Nominally each song or `geotrack` is an Audio Object (or Digital Music Object), which could be used in a range of experiences; the `composition` defines how a set of geo-tracks are combined and situated for a /specific/ experience. 

Often only a single `geotrack` will be active at any particular moment. The player, based on the `composition`, will handle playing, pausing and stopping `geotrack`s, and will also provide appropriate control inputs for active geotracks, typically based on sensor inputs.

## GeoTrack Interface

From the perspective of a composition a `geotrack` would be seen in terms of a public interface including:

- a unique ID;
- metadata, e.g. title, key, time signature, tempo, genre, author, license
- a set of one or more externally visible `sections`s (or states) and their relationships and characteristics;
- a set of zero or more control or input parameters.

A `geotrack` might have only a single external `section` (e.g. "the whole song"), even if it has more sub-sections/scenes internally.

A `geotrack` may be in one of the following overall states:
- `unprepared`
- `preparing`
- `prepared`
- `playing`
- `ended`

At any moment in time a track may have a current section (and time within that section) and/or a next section (and time of transition).

Each section may have the following public properties:
- ID (unique within this `geotrack`)
- metadata, e.g. title, key, time signature, tempo
- minimum duration
- maximum duration
- valid start, i.e. whether you can start with this section
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

Controls are defined at the `geotrack` level, but may only apply to a subset of `section`s (section controls).

Control is defined as:
- TODO

## Composition Model

A composition includes:

- an abstract graph of `geotracks` and transitions that comprise the whole experience, including their non-location controls
- a set of `waypoint`s and a graph of `route`s over those `waypoint`s
- a linkage from the `route` graph to the `geotrack` graph which specifies placement
- specification of contingencies, esp. loss of GPS, divergence from `route`s.

TODO...

## GeoTrack Definition / Implementation

Internally, a `geotrack` would comprise or be realised as:

- a header (metadata)
- some configuration parameters
- one or more audio `track`s, i.e. audio streams;
- one or more `scene`s, i.e. potentially dynamic configurations of tracks roughly corresponding to distinct states of the `geotrack`.


