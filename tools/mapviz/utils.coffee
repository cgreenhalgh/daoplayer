# utils.coffee
# http://wiki.openstreetmap.org/wiki/Mercator#JavaScript
module.exports.deg_rad = deg_rad = (ang) ->
  ang * (Math.PI/180.0)

r_major = 6378137.000
r_minor = 6356752.3142

module.exports.merc_x = merc_x = (lon) ->
  r_major * deg_rad(lon)

module.exports.merc_y = merc_y = (lat) ->
    if lat > 89.5
        lat = 89.5
    if lat < -89.5
        lat = -89.5
    temp = r_minor / r_major
    es = 1.0 - (temp * temp)
    eccent = Math.sqrt(es)
    phi = deg_rad(lat)
    sinphi = Math.sin(phi)
    con = eccent * sinphi
    com = 0.5 * eccent
    con = Math.pow((1.0-con)/(1.0+con), com)
    ts = Math.tan(.5 * (Math.PI*0.5 - phi))/con
    y = 0 - r_major * Math.log(ts)
    return y

module.exports.merc = merc = (x,y) ->
    [merc_x(x),merc_y(y)]

# cludgy way to check how big a metre is in merc x,y at a lat,lng
module.exports.merc_metre = merc_metre = (lat,lon) ->
    angle = Math.PI/2/r_major
    if (lat > 89.5 || lat < -89.5)
        return Number.NaN
    if (lat < 0)
        angle = -angle
    # straight-line approx. on surface of elipsoid 
    dsx = r_major*(Math.sin(deg_rad(lat+angle))-Math.sin(deg_rad(lat)))
    dsy = r_minor*(Math.cos(deg_rad(lat+angle))-Math.cos(deg_rad(lat)))
    d = Math.sqrt(dsx*dsx+dsy*dsy)
    dmy = merc_y(lat+angle)-merc_y(lat)
    if (dmy<0)
        dmy = -dmy
    d/dmy

