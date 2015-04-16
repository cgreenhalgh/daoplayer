// daoplayer log -> tsv
//
// Chris Greenhalgh, The University of Nottingham, 2015.
//
// exports raw gps location reports from on.location events
// outputs: 
//   time (unix ms) 
//   event ('gps') 
//   lat (degrees) 
//   lng (degrees) 
//   x (metres, relative to first report)
//   y (metres, relative to first report)
//   accuracy (metres)
//
// Usage:
//   node log2tsv.js <daoplayerlogfile> <outfile.tsv>

var fs = require("fs");

if (process.argv.length!=4) {
  console.log("usage: <logfile> <outfile>");
  process.exit(-1);
}

//http://wiki.openstreetmap.org/wiki/Mercator#JavaScript
function deg_rad(ang) {
    return ang * (Math.PI/180.0)
}
function merc_x(lon) {
    var r_major = 6378137.000;
    return r_major * deg_rad(lon);
}
function merc_y(lat) {
    if (lat > 89.5)
        lat = 89.5;
    if (lat < -89.5)
        lat = -89.5;
    var r_major = 6378137.000;
    var r_minor = 6356752.3142;
    var temp = r_minor / r_major;
    var es = 1.0 - (temp * temp);
    var eccent = Math.sqrt(es);
    var phi = deg_rad(lat);
    var sinphi = Math.sin(phi);
    var con = eccent * sinphi;
    var com = .5 * eccent;
    con = Math.pow((1.0-con)/(1.0+con), com);
    var ts = Math.tan(.5 * (Math.PI*0.5 - phi))/con;
    var y = 0 - r_major * Math.log(ts);
    return y;
}
function merc(x,y) {
    return [merc_x(x),merc_y(y)];
}
// cludgy way to check how big a metre is in merc x,y at a lat,lng
function merc_metre(lat,lon) {
    var r_major = 6378137.000;
    var r_minor = 6356752.3142;
    var angle = Math.PI/2/r_major;
    if (lat > 89.5 || lat < -89.5)
        return Number.NaN;
    if (lat < 0)
        angle = -angle;
    // straight-line approx. on surface of elipsoid 
    var dsx = r_major*(Math.sin(deg_rad(lat+angle))-Math.sin(deg_rad(lat)));
    var dsy = r_minor*(Math.cos(deg_rad(lat+angle))-Math.cos(deg_rad(lat)));
    var d = Math.sqrt(dsx*dsx+dsy*dsy);
    var dmy = merc_y(lat+angle)-merc_y(lat);
    if (dmy<0)
        dmy = -dmy;
    return d/dmy;
}

var logfilename = process.argv[2];
var outfilename = process.argv[3];
console.log("read "+logfilename);
var logfile = fs.readFileSync(logfilename,{encoding:"utf-8"});
var lines = logfile.split("\n");
var outfile = fs.openSync(outfilename,"w",{encoding:"utf-8"});
fs.writeSync(outfile,"time\tevent\tlat\tlng\tx\ty\taccuracy\n");
var ref = null;
var metre = 1;
var refx, refy;
for(var i=0; i<lines.length; i++) {
  try {
    var rec = JSON.parse(lines[i]);
    var date = new Date(rec.datetime);
    var event = rec.event;
    if (event=='on.location') {
      // first as ref
      if (ref==null) {
        ref = rec;
        meter = merc_metre(rec.info.lat,rec.info.lng);
        refx = merc_x(rec.info.lng);
        refy = merc_y(rec.info.lat);
      }
      // lng, lat, accuracy
      // mercator projection
      var x = (merc_x(rec.info.lng)-refx)/meter;
      var y = (merc_y(rec.info.lat)-refy)/meter;
      fs.writeSync(outfile,date.getTime()+"\tgps\t"+rec.info.lat+"\t"+rec.info.lng+"\t"+x+"\t"+y+"\t"+rec.info.accuracy+"\n");
    } else if(event=='on.gpsStatus') {
      //if (rec.info.changeUsedInFix)
      //  fs.writeSync(outfile,date.getTime()+"\tchangeUsedInFix\t\t\t\t\t\n");
    }
  } catch (err) {
    console.log("Error parsing line "+i+": "+err.message);
  }
}
fs.close(outfile);

