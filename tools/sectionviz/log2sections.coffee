# log2sections.coffee
# 
# Chris Greenhalgh, The University of Nottingham, 2015
# 
# Convert doaplayer log to select sections info for visualiation
#
# Usage:
#  coffee log2sections.coffee <composition.json> <trackname> <logfile> <outfile.json>
#
# Output json object with
# - selections - array

fs = require "fs" 

if process.argv.length!=6
  console.log "usage: <composition.json> <trackname> <logfile> <outfile.json>"
  process.exit -1

compfilename = process.argv[2]
trackname = process.argv[3]
logfilename = process.argv[4]
outfilename = process.argv[5]
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

sections = {}
# fix missing lengths
for section,si in track.sections
  if not section.length and si+1<track.sections.length
    section.length = track.sections[si+1].trackPos
  sections[section.name] = section

output = {}
output.selections = []
output.track = track

logfile = fs.readFileSync logfilename, encoding:"utf-8"
loglines = logfile.split "\n"
for logline,i in loglines
  try
    rec = JSON.parse logline
    date = new Date rec.datetime
    event = rec.event
    if event=='sections.select' and rec.info.trackName==trackname
      info = rec.info
      selection = 
        trackName: trackname
        totalTime: info.totalTime
        sceneTime: info.sceneTime
        currentSectionName: info.currentSectionName
        currentSectionTime: info.currentSectionTime
        targetDuration: info.targetDuration
      selection.sections = []
      output.selections.push selection
      startTime = info.sceneTime
      for r,ri in info.result
        if typeof r == 'number'
          startTime = r
          continue
        section =
          name: r
          startTime: startTime
        sec = sections[section.name]
        if sec?
          section.length = sec.length
          section.trackPos = sec.trackPos
          selection.sections.push section
          startTime += section.length
        else
          console.log "Error: reference to unknown section   "+section.name+" of track "+trackname
  catch err
    console.log "Error parsing "+logfilename+" line "+i+": "+logline+": "+err.message 

console.log "write "+outfilename
outfile = JSON.stringify output
fs.writeFileSync outfilename, outfile, encoding:"utf-8"

