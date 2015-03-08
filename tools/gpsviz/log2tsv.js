// daoplayer gps log -> tsv
// node
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

var logfilename = process.argv[2];
var outfilename = process.argv[3];
console.log("read "+logfilename);
var logfile = fs.readFileSync(logfilename,{encoding:"utf-8"});
var lines = logfile.split("\n");
var outfile = fs.openSync(outfilename,"w",{encoding:"utf-8"});
fs.writeSync(outfile,"time\tevent\tlat\tlng\tx\ty\taccuracy\n");
for(var i=0; i<lines.length; i++) {
  try {
    var rec = JSON.parse(lines[i]);
    var date = new Date(rec.datetime);
    var event = rec.event;
    if (event=='on.location') {
      // lng, lat, accuracy
      // mercator projection
      var x = merc_x(rec.info.lng);
      var y = merc_y(rec.info.lat);
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

