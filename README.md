# gif-capture
utility to capture screen and write to gif

`sbt run`, drag a selection, then hit enter when you're done recording.

The default filename is `/tmp/output.gif`. This can be overriden by passing a parameter to run:

`sbt run outputfile.gif`

NOTE: On OSX, screen recording permission needs to be given to the console app
e.g., iTerm2, under privacy settings in system preferences.
