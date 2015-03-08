//simple http server
//http://stackoverflow.com/questions/6084360/using-node-js-as-a-simple-web-server
var http = require('http'),
fs = require('fs'),
url = require('url'),
rootFolder = '/',
defaultFileName = rootFolder+'index.html';

http.createServer(function(req, res){

    var fileName = url.parse(req.url).pathname;
    // If no file name in Url, use default file name
    fileName = (fileName == "/") ? defaultFileName : rootFolder + fileName;

    fs.readFile(__dirname + fileName, 'binary',function(err, content){
        if (content != null && content != '' ){
            res.writeHead(200,{'Content-Length':content.length});
            res.write(content);
        }
        res.end();
    });

}).listen(8800);
console.log("server running on port 8800");
