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
users = output.users = {}
nextuserid = 0
output.waypoints = []
output.routes = []
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
    waypoints = {}
    if comp.context?.waypoints?
      for waypoint in comp.context.waypoints
        if waypoint.origin and not output.origin?
          output.origin = waypoint
        output.waypoints.push waypoint
        if waypoint.name?
          waypoints[waypoint.name] = waypoint
    if comp.context?.routes?
      for route in comp.context.routes
        # name, from, to
        route.fromwaypoint = waypoints[route.from]
        route.towaypoint = waypoints[route.to]
        output.routes.push route
  else if section=='LOGFILES' and configline!=''
    logpat = /^(\S+)\s+(.+)/
    logline = logpat.exec configline
    if logline
      user = logline[1]
      logfilename = logline[2]
      userid = users[user]?.id
      if not userid?
        userid = ++nextuserid
        users[user] = {id: userid}
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
          if date? and ( not users[user].mintime? or date.getTime()<users[user].mintime)
            users[user].mintime = date.getTime()
          if event=='update.position'
            info = rec.info
            info.logtime = date.getTime()
            info.user = user
            info.userid = userid
            if lastposition?
              info.lastposition = lastposition
              info.lastlat = lastposition.lat
              info.lastlng = lastposition.lng
              info.lastlogtime = lastposition.logtime
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
  metre = 1
  if origin?
    refx = utils.merc_x origin.lng
    refy = utils.merc_y origin.lat
    metre = utils.merc_metre origin.lat, origin.lng
    console.log "reference metre = "+metre
  for waypoint in waypoints
    waypoint.x = ((utils.merc_x waypoint.lng)-refx)*metre
    waypoint.y = ((utils.merc_y waypoint.lat)-refy)*metre

fix_waypoints output.waypoints, output.origin

# fix position positions
fix_positions = (positions, origin) ->
  refx = 0
  refy = 0
  metre = 1
  if origin?
    refx = origin.refx = utils.merc_x origin.lng
    refy = origin.refy = utils.merc_y origin.lat
    metre = origin.metre = utils.merc_metre origin.lat, origin.lng
  for position in positions
    position.x = ((utils.merc_x position.lng)-refx)*metre
    position.y = ((utils.merc_y position.lat)-refy)*metre
    if position.lastlng?
      position.lastx = ((utils.merc_x position.lastlng)-refx)*metre
    if position.lastlat?
      position.lasty = ((utils.merc_y position.lastlat)-refy)*metre
    if position.gpslng?
      position.gpsx = ((utils.merc_x position.gpslng)-refx)*metre
    if position.gpslat?
      position.gpsy = ((utils.merc_y position.gpslat)-refy)*metre

fix_positions output.positions, output.origin

fix_times = (output) ->
  for position in output.positions
    if position.user? and output.users[position.user]?.mintime?
      if position.logtime? 
        position.usertime = position.logtime-output.users[position.user].mintime
      if position.lastlogtime? 
        position.lastusertime = position.lastlogtime-output.users[position.user].mintime
  for scene in output.scenes 
    if scene.user? and scene.logtime? and output.users[scene.user]?.mintime?
      scene.usertime = scene.logtime-output.users[scene.user].mintime

fix_times output

map_to_route = (output) ->
  if output.routes.length>0
    totaldistanceAlong = 0
    for route in output.routes
      if route.fromwaypoint? and route.towaypoint?
        rx = route.towaypoint.x-route.fromwaypoint.x
        ry = route.towaypoint.y-route.fromwaypoint.y
        route.length = Math.sqrt( rx*rx+ry*ry )
        route.fromwaypoint.totaldistanceAlong = totaldistanceAlong
        route.totaldistanceAlong = totaldistanceAlong
        totaldistanceAlong += route.length
        route.towaypoint.totaldistanceAlong = totaldistanceAlong
    for position in output.positions
      bestroute = null
      bestdistanceFrom = 0
      bestdistanceAlong = 0
      bestdistance = 0
      besttotaldistanceAlong
      for route in output.routes
        if route.fromwaypoint? and route.towaypoint?
          dx = position.x-route.fromwaypoint.x
          dy = position.y-route.fromwaypoint.y
          rx = route.towaypoint.x-route.fromwaypoint.x
          ry = route.towaypoint.y-route.fromwaypoint.y
          if route.length>0
            dot = dx*rx/route.length+dy*ry/route.length
            onx = dot*rx/route.length
            ony = dot*ry/route.length
            distance = Math.sqrt((dx-onx)*(dx-onx)+(dy-ony)*(dy-ony))
            distanceAlong = dot
            if dot<0
              distanceAlong = 0
            else if dot>route.length 
              distanceAlong = route.length
            else
              distanceAlong = dot
            onx = distanceAlong*rx/route.length
            ony = distanceAlong*ry/route.length
            distanceFrom = Math.sqrt((dx-onx)*(dx-onx)+(dy-ony)*(dy-ony))
          else
            # zero length
            distanceFrom = distance = Math.sqrt(dx*dx+dy*dy)
            distanceAlong = 0
          if bestroute==null or distanceFrom<bestdistanceFrom
            bestroute = route
            bestdistanceFrom = distanceFrom
            bestdistanceAlong = distanceAlong
            bestdistance = distance
            besttotaldistanceAlong = route.totaldistanceAlong+distanceAlong
      position.route = bestroute.name
      position.distanceFrom = bestdistanceFrom
      position.distanceAlong = bestdistanceAlong
      position.distance = bestdistance
      position.totaldistanceAlong = besttotaldistanceAlong
      position.lasttotaldistanceAlong = position.lastposition?.totaldistanceAlong
      position.lastdistanceFrom = position.lastposition?.distanceFrom
      delete position.lastposition

map_to_route output

console.log "write "+outfilename
outfile = JSON.stringify output
fs.writeFileSync outfilename, outfile, encoding:"utf-8"

