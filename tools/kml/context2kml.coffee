# context2kml.coffee
#
# Read daoplayer context and output KML file

fs = require "fs" 
EasyXml = require 'easyxml'

if process.argv.length!=4
  console.log "usage: <composition-file-in> <kml-file-out>"
  process.exit -1

infilename = process.argv[2]
outfilename = process.argv[3]
console.log "read "+infilename
infile = fs.readFileSync infilename, encoding:"utf-8"
comp = JSON.parse infile
if not comp.context?
  console.log "Input has no context object"
  process.exit -2
if not comp.context.waypoints?
  console.log "Input has no waypoints"
  process.exit -2

serializer = new EasyXml
    singularize: true,
    rootElement: 'kml',
    dateFormat: 'ISO',
    manifest: true,
    unwrapArrays: true

placemarks = [
#  name: 'pt1'
#  Point:
#    Coordinates: '0,0,0'
]

waypoints = {}
for waypoint in comp.context.waypoints
  if waypoint.name?
    waypoints[waypoint.name] = waypoint
  placemarks.push
    name: waypoint.name,
    description: waypoint.description,
    Point:
      coordinates: ''+waypoint.lng+','+waypoint.lat+',0'

for route in comp.context.routes?=[]
  if route.from? and route.to? and waypoints[route.from]? and waypoints[route.to]?
    from = waypoints[route.from]
    to = waypoints[route.to]
    placemarks.push
      name: route.name,
      description: route.description
      LineString:
        coordinates: from.lng+','+from.lat+',0 '+to.lng+','+to.lat+',0'
  else
    console.log 'Warning: ignore incomplete route '+route.name

kml = 
  Document:
    name: 'Context from '+infilename
    Folder:
      name: 'context'
      Placemark: placemarks
    
kmltext = serializer.render kml
console.log "write "+outfilename
fs.writeFileSync outfilename, kmltext, encoding:"utf-8"