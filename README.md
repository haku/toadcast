Toadcast
========

Makes a Chromecast look like a normal DLNA renderer.
Handy if you have a media system that understands DLNA renderers that you want to use with a Chromecast (both Audio and Video editions).

* Tracks Chromecast by name, so should rediscover it when IP address changes.
* Finds Chromecast via either mDNS or UPnP (which ever responds first).
* Should pause and resume correctly even after Chromecast app has idle timed out.
* Should not interfere with other Chromecast clients when idle.
* Should recover and resume playback if Chromecast disconnects or power cycles.

Build
-----

Its a typical Java Maven project.
```bash
mvn clean package
```

Run
---

Use the `-c` argument to specify a substring of the Chromecast's friendly name.  So if your Chromecast is called "Foo Bar", use something like:
```bash
java -jar ./target/toadcast-1-SNAPSHOT-jar-with-dependencies.jar -c bar
```

If it fails to discover the Chromecast, try specifying the interface to use with the `net.mdns.interface` property, e.g.:
```bash
java -Dnet.mdns.interface=192.168.0.213 -jar ./target/toadcast-1-SNAPSHOT-jar-with-dependencies.jar -c bar
```
