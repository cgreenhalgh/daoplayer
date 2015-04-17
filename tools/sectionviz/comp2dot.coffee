# comp2dot.coffee
# 
# Chris Greenhalgh, The University of Nottingham, 2015
# 
# Convert sections from a track in a composition to a dot file for visualization
# Usage:
#  coffee comp2dot.coffee <composition.json> <trackname> <outfile.dot>

fs = require "fs" 

if process.argv.length!=5
  console.log "usage: <composition.json> <trackname> <outfile.dot>"
  process.exit -1

compfilename = process.argv[2]
trackname = process.argv[3]
outfilename = process.argv[4]
console.log "read "+compfilename
compfile = fs.readFileSync compfilename, encoding:"utf-8"
comp = JSON.parse compfile
track = null
for t in comp.tracks ? [] when t.name==trackname
  track = t
if not track?
  console.log "Could not find track "+trackname+" in "+compfilename
  process.exit -2
if not track.sections?
  console.log "Track "+trackname+" has no sections"
  process.exit -2

outfile = fs.createWriteStream outfilename, 
    flags: 'w',
    encoding: 'utf-8', 
    mode: 0o644
outfile.write "digraph {\n"

console.log "write circo skeleton to "+outfilename+".circo"
outfile2 = fs.createWriteStream outfilename+'.circo', 
    flags: 'w',
    encoding: 'utf-8', 
    mode: 0o644
outfile2.write "digraph {\n"

# start
outfile.write "  START [ ]\n"
outfile.write "  END [ ]\n"
outfile2.write "  START [ ]\n"
outfile2.write "  END [ ]\n"
#outfile.write "  END -> START [style=dotted]\n"
outfile2.write "  END -> START [style=dotted]\n"
outfile.write "  node [rank=same;ordering=in]\n"
outfile2.write "  node [rank=same;ordering=in]\n"

defaultNextSectionCost = track.defaultNextSectionCost ?= 0
defaultEndCost = track.defaultEndCost ?= 10000

HIGH_START = 100000000

for section, si in track.sections
  if section.name?
    outfile.write '  '+section.name+' [ shape=record; label="{'+section.name+'|'+section.trackPos+'|'+section.length+'}" ]\n'
    outfile2.write '  '+section.name+' [ shape=record; label="{'+section.name+'|'+section.trackPos+'|'+section.length+'}" ]\n'

for section, si in track.sections
  if section.name?
    startCost = section.startCost ? (if si==0 then 0 else HIGH_START)
    if startCost < HIGH_START
      outfile.write '  START -> '+section.name+' [ label="'+startCost+'" ]\n'
    endCost = section.endCost ? defaultEndCost
    eclabel = if section.endCostExtra then ''+endCost+'+'+section.endCostExtra else ''+endCost
    outfile.write '  '+section.name+' -> END [ label="'+eclabel+'" '+(if not section.endCost? then ';style=dashed' else '')+' ]\n'
    # next section
    nextSection = if si+1<track.sections.length then track.sections[si+1] else null
    if si==0
      outfile2.write "  START -> "+section.name+" [style=dotted]\n"
      #outfile.write "  START -> "+section.name+" [style=dotted]\n"
    if nextSection?
      outfile2.write "  "+section.name+" -> "+nextSection.name+" [style=dotted]\n"
      #outfile.write "  "+section.name+" -> "+nextSection.name+" [style=dotted]\n"
    else
      outfile2.write "  "+section.name+" -> END [style=dotted]\n"
      #outfile.write "  "+section.name+" -> END [style=dotted]\n"

    nextSectionDone = false
    for next in section.next ? []
      if next.name == nextSection.name
        nextSectionDone = true
      cost = next.cost ? 0
      outfile.write '  '+section.name+' -> '+next.name+' [ label="'+cost+'" ]\n'
      found = false
      for s in track.sections when s.name==next.name
        found = true
      if not found
        console.log "Error: reference to unknown next section "+next.name+" from "+section.name
    if not nextSectionDone
      outfile.write '  '+section.name+' -> '+next.name+' [ label="'+cost+'";shape=dashed ]\n'
   
outfile.write "}\n"
outfile.end()
outfile2.write "}\n"
outfile2.end()
console.log "wrote "+outfilename

