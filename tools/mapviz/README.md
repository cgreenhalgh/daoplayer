# DaoPlayer MapViz

Visualise processed logs on a map.

Config file format:

```
COMPOSITIONS
<compfilename.json>
...
LOGFILES
<userid> <logfile>
...
```

Usage: `node mapvizlogs.js <configfile> <outfile>`

run `node ../../httpserver.js`

View `http://localhost:8800/tools/sectionviz/mapviz.html?f=<outfile>`


