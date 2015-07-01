# DaoPlayer Tools

Tools, mainly for working with daoplayer logs.

- mapviz/mapvizlogs.coffee - process daoplayer log plus composition context to file for viewing with mapviz/map.html or mapviz/route.html
- mapviz/map.html - map view of mapviz log of user positions and waypoints
- mapviz/route.html - timeline view of mapviz log of user distance along and from route

- sectionviz/comp2dot.coffee - convert sections/costs for a track into a dot file for graphviz
- sectionviz/log2sections.coffee - convert a daoplayer log file plus composition into a file for sections.html
- sectionviz/sections.html - render sections file to show sections chosen over time for a particular track in a particular log
- sectionviz/trackpos.html - render sections file to show track time played over time for a particular track in a particular log

- src/gps/TestFilter.java - re-run user model (inc kalman filter) over daoplayer log and output json-encoded waypoints and locations
- gpsviz/log2tsv.js - export on.location events from daoplayer log as TSV file
- gpsviz/index.html - d3-based visualisation of TestFilter output file to check kalman filter operation

Better HTTP Server:

```
npm install http-server
node node_modules/.bin/http-server .
```
