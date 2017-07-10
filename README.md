Extensive XMPP Client Framework for Android and Java SE
=======================================================

`libviska-java` is a set of Java libraries for developing an XMPP client. It 
aims to support as many modern or experimental features as possible. It is 
distinct from other counterparts by the following reasons:

  * [Jingle](https://wiki.xmpp.org/web/Tech_pages/Jingle) support is first-class
    citizen.
  * Rapidly adopt features or standards that are still experimental. We believe 
    this can accelerate the evolution of XMPP standards.
  * Designed for being used by both Java SE and Android applications.
  * XEPs are implemented as plugins and work in a plug-and-play manner, which
    gives high extensibility.

This library can be used in Android API Level >= 19 or Java SE >= 7.

License
-------

`libviska-java` is free software and is licensed under Apache-2.0 License. The
license is available in the `LICENSE` file.

Contributing
------------

The team behind the Viska project is relatively small compared to its
proprietary counterparts who are backed by large companies. Therefore, we
welcome all kinds of help!

### How to Build

Simply run

```bash
gradle
```

This builds the binaries and the project-wise Javadoc without running any tests.