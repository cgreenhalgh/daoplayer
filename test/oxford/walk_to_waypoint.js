// requires window.nextWaypoint (name) & window.nextScene
daoplayer.log('walk_to_waypoint '+window.nextWaypoint+'; near='+waypoints[window.nextWaypoint].near+', relativeBearing='+waypoints[window.nextWaypoint].relativeBearing+', lastWarning='+window.lastWarning); 

if (waypoints[window.nextWaypoint].near) {
  daoplayer.setScene(window.nextScene);
};
if (activity=='WALKING' && waypoints[window.nextWaypoint].relativeBearing!==undefined &&
    (waypoints[window.nextWaypoint].relativeBearing>90 || waypoints[window.nextWaypoint].relativeBearing< -90) &&
    (window.lastWarning===undefined || window.lastWarning===null || totalTime-window.lastWarning>10)) {
  daoplayer.log('warn'); 
  window.lastWarning=totalTime;
  daoplayer.speak('you seem to be walking the wrong way',true);
};
if (activity=='WALKING' && waypoints[window.nextWaypoint].relativeBearing!==undefined &&
    (waypoints[window.nextWaypoint].relativeBearing<45 && waypoints[window.nextWaypoint].relativeBearing> -45) &&
    (window.lastWarning!==undefined && window.lastWarning!==null)) {
  daoplayer.log('assure'); 
  window.lastWarning = null;
  daoplayer.speak('OK, you seem to be walking the right way',true);
};
