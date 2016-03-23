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

compile: `coffee -c *.coffee`

Usage: `node mapvizlogs.js <configfile> <outfile>`

run `(cd /vagrant; `npm bin`/http-server /)`

View `http://localhost:8080/vagrant/tools/sectionviz/mapviz.html?f=<outfile>`


