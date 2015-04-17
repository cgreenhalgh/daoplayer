# Section visualisation

## Authored

Use dot to visualise authored sections and transitions
```
coffee comp2dot.coffee <composition.json> <trackname> <outfile.dot>
```
Also outputs `<outfile.dot>.circo`.

Try, e.g. to make `<outfile.dot>.png`
```
dot <outfile.dot> -Tpng -O
```
or
```
circo <outfile.dot> -Tpng -O
```

For circle view preserving section order:
```
circo <outfile.dot>.circo > <outfile.dot>.placed
```
Edit `<outfile.dot>.placed` and paste links from `<outfile.dot>` before final closing bracket, then
```
neato -n <outfile.dot>.placed -Tpng -O
```

## Actual

Use custom d3 visualisation to show online section selection

```
coffee log2sections.coffee <composition.json> <trackname> <logfile> <outfile.json>
```


run `node ../../httpserver.js`

Changing plan against time:

View `http://localhost:8800/tools/sectionviz/sections.html?f=<outfile>`


Actual playout - trackTime vs experience time:

View `http://localhost:8800/tools/sectionviz/trackpos.html?f=<outfile>`

