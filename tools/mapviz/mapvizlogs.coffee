# mapvizlogs.coffee
#
# Chris Greenhalgh, The University of Nottingham, 2015
#
# Read config file (see README.md), process logs and output JSON for visualisation with:
# - origin (done) - origin waypoint
# - waypoints (done) - array of waypoints
# - routes - array of routes
# - users - (done) array of users
# - positions - (done) array of positions
# - scenes - (done) array of scene changes

fs = require "fs" 
utils = require "./utils"

if process.argv.length!=4
  console.log "usage: <configfile> <outfile>"
  process.exit -1

configfilename = process.argv[2]
outfilename = process.argv[3]
console.log "read "+configfilename
configfile = fs.readFileSync configfilename, encoding:"utf-8"
configlines = configfile.split "\n"
section = ''
output = {}
users = {}
nextuserid = 0
output.waypoints = []
output.positions = []
output.scenes = []
for configline in configlines
  configline = configline.trim()
  if configline=='COMPOSITIONS' || configline=='LOGFILES'
    section = configline
  else if section=='COMPOSITIONS' and configline!=''
    compfilename = configline
    console.log "read composition "+compfilename
    compfile = fs.readFileSync compfilename, encoding:"utf-8"
    comp = JSON.parse compfile
    if comp.context?.waypoints?
      for waypoint in comp.context.waypoints
        if waypoint.origin and not output.origin?
          output.origin = waypoint
        output.waypoints.push waypoint
  else if section=='LOGFILES' and configline!=''
    logpat = /^(\S+)\s+(.+)/
    logline = logpat.exec configline
    if logline
      user = logline[1]
      logfilename = logline[2]
      userid = users[userid]
      if not userid?
        userid = ++nextuserid
        users[user] = userid
        console.log "added user "+user+" as "+userid
      console.log "read logfile "+logfilename
      logfile = fs.readFileSync logfilename, encoding:"utf-8"
      lastposition = null
      lastgps = null
      loglines = logfile.split "\n"
      lastscene = null
      for logline,i in loglines
        try
          rec = JSON.parse logline
          date = new Date rec.datetime
          event = rec.event
          if event=='update.position'
            info = rec.info
            info.logtime = date.getTime()
            info.user = user
            info.userid = userid
            if lastposition?
              info.lastlat = lastposition.lat
              info.lastlng = lastposition.lng
            lastposition = info
            if lastgps?
              info.gpslat = lastgps.lat
              info.gpslng = lastgps.lng
            output.positions.push info
            if lastscene?
              lastscene.position = info
              lastscene = null
          else if event=='on.location'
            lastgps = rec.info
          else if event=='scene.set'
            info = { name: rec.info }
            info.logtime = date.getTime()
            info.user = user
            info.userid = userid
            if lastposition?
              info.position = lastposition
              lastscene = null
            else
              lastscene = info
            output.scenes.push info
        catch err
          console.log "Error parsing "+logfilename+" line "+i+": "+logline+": "+err.message 
    else
      console.log "Invalid LOGFILES line: "+configline
      process.exit -2

# fix waypoint positions
fix_waypoints = (waypoints, origin) ->
  refx = 0
  refy = 0
  if origin?
    refx = utils.merc_x origin.lng
    refy = utils.merc_y origin.lat
    metre = utils.merc_metre origin.lat, origin.lng
  for waypoint in waypoints
    waypoint.x = ((utils.merc_x waypoint.lng)-refx)/metre
    waypoint.y = ((utils.merc_y waypoint.lat)-refy)/metre

fix_waypoints output.waypoints, output.origin

# fix position positions
fix_positions = (positions, origin) ->
  refx = 0
  refy = 0
  if origin?
    refx = utils.merc_x origin.lng
    refy = utils.merc_y origin.lat
    metre = utils.merc_metre origin.lat, origin.lng
  for position in positions
    position.x = ((utils.merc_x position.lng)-refx)/metre
    position.y = ((utils.merc_y position.lat)-refy)/metre
    if position.lastlng?
      position.lastx = ((utils.merc_x position.lastlng)-refx)/metre
    if position.lastlat?
      position.lasty = ((utils.merc_y position.lastlat)-refy)/metre
    if position.gpslng?
      position.gpsx = ((utils.merc_x position.gpslng)-refx)/metre
    if position.gpslat?
      position.gpsy = ((utils.merc_y position.gpslat)-refy)/metre

fix_positions output.positions, output.origin

console.log "write "+outfilename
outfile = JSON.stringify output
fs.writeFileSync outfilename, outfile, encoding:"utf-8"

