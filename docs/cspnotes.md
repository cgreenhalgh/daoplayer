# DaoPlayer CSP Notes

Thinking about using something like Communicating Sequential Processes as a paradigm for specifying what DaoPlayer should play, for example during a journey where exact timing depends on the specific user's moment-by-moment actions (especially walking, stopping, changing speed, pausing).

## The Idea

I want a (mostly) declarative notation for specifying a mobile soundtrack, in particular one that adapts dynamically to the listeners progress on a particular (anticipated journey or route). The soundtrack is built up from a sequence of distinct elements, e.g. tracks. But in places the soundtrack should be synchronized to particular physical locations on the journey. 

Perhaps the soundtrack can be expressed as a genertive grammar for how music tracks and fragments can be combined, combined with constraints (e.g. 'at the cross roads') and priorities.

Perhaps something like CSP would be a useful starting point for this notation. Or perhaps not :-)

## CSP Background

[CSP](http://en.wikipedia.org/wiki/Communicating_sequential_processes) is a process calculus due to Hoare. [Hoare's book](http://www.usingcsp.com/cspbook.pdf) is publicly available, as is a similar [book by Roscoe ](http://www.cs.ox.ac.uk/bill.roscoe/publications/68b.pdf). 

There is a machine-readable dialect of CSP, CSPM, which is widely used in tools such as FDR. It is described in Appendix B of [Roscoe's book](http://www.cs.ox.ac.uk/bill.roscoe/publications/68b.pdf), on the [FDR website](http://www.cs.ox.ac.uk/projects/fdr/manual/cspm.html) and a few other places.

Some of the main elements of the process (CSP) subset of CSPM (which also includes general functional programming stuff):

- `P [] Q` - external choice of process `P` and `Q`
- `b & P` - if boolean expression `b` is true then `P` (else `STOP`)
- `P \ A` - process `P` with events in `A` (set of events) hideen
- `P |~| Q` - internal choice of process `P` and `Q`, i.e. may act as either
- `e -> P` - perform event `e` and then act as process `P`
- `channel c : String` - (e.g.) declare channel `c` with type `String`, i.e. events `c.'s'` where `'s'` is any valid String
- `c!e` - output `e` on channel `c`, i.e. event `c.e`
- `c?x` - input `x` on channel `c`, i.e. some event `c.e` where `x` is bound to `e`
- `P[[from <- to]]` - P with event `from` renamed to `to`
- `P [| A |] Q` - process `P` parallel with process `Q`, synchronised on event set `A`
- `P ||| Q` - process `P` interleaved with `Q`, unsynchronized
- `P [| A |> Q` - (Exception) process `P`, but if `P` performs an event in `A` then it is stopped and becomes process `Q`
- `P /\ Q` - (Interrupt) process `P`, but if process `Q` ever performs an event then `P` is stopped and becomes `Q`
- `P ; Q` - (Sequential Composition) process `P`, then process `Q`
- `P [> Q` - (Sliding choice or Timeout) process `P` which may non-deterministically become `Q`

There are also some timed variants of CSP, including tock-CSP, a discrete time variant, and [Timed-CSP](https://www.cs.ox.ac.uk/files/3426/PRG96.pdf), a continuous time variant.

## CSP Mapping Version 1

A soundtrack is a CSP process.

The main event(s) are to play (output) a particular segment of music, which may be an entire track or just a bar or a beat. Clearly time is significant in this case, while CSP events are instantaneous, so some sort of time aspect will be required.

## Tentative Conclusion

Inspiring but probably not directly useful? Concurrency isn't obviously important at the moment. Sequence, yes. Processes and names, yes. Interrupts, in some cases.


