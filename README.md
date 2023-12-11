# WorldMap
WorldMap is a simple Android app that displays a map of the world taken from Wikimedia (http://upload.wikimedia.org/wikipedia/commons/3/33/Physical_Political_World_Map.jpg), and allows the user to scroll around on it. Note the map is from wikipedia and licensed as public domain (see http://en.wikipedia.org/wiki/File:Physical_Political_World_Map.jpg).

The map itself is quite large (6480,3888), so it's way too big to fit in memory all at once (6480 x 3888 x 32 / 8) = 100,776,960 -- over 96 megs. The VM heap size Android supports is eith 16 or 24 megs, so we can't fit the whole thing in memory at once.

So WorldMap uses the BitmapRegionDecoder API (available as of API 10) to decode just what it needs to display.

WorldMap is available on the Google Play store here: https://play.google.com/store/apps/details?id=com.sigseg.android.worldmap

# Road Map
--------
* Add flinging to move quickly across the map

* Work on getting the frame rate up
  * Perhaps use SurfaceView
  * Or glSurfaceView

* backport BitmapRegionDecoder to 2.2 or use a different library with JNI.
  * on SO, Dianne Hackborn says this is non trivial.

* Add zooming to infinite levels
  * start with the map fully unzoomed
  * Calculate where in the world we are once we get to a certain level, then create an intent to start Google Maps.
