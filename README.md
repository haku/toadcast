Toadcast
========

Make a ChromeCast look like a normal DLNA renderer.
Handy if you have a media system that understands DLNA renderers that you want to use with a ChromeCast Audio.

Build
-----

Its a typical Java Maven project.
```bash
mvn clean package
```

Run
---

Use the `-c` argument to specify a substring of the ChromeCast's friendly name.  So if your ChromeCast is called "Foo Bar", use something like:
```bash
java -jar ./target/toadcast-1-SNAPSHOT-jar-with-dependencies.jar -c bar
```

If it fails to discover the ChromeCast, try specifying the interface to use with the `net.mdns.interface` property, e.g.:
```bash
java -Dnet.mdns.interface=192.168.0.213 -jar ./target/toadcast-1-SNAPSHOT-jar-with-dependencies.jar -c bar
```
