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

This library can be used on Android (API Level >= 26) or (Java SE >= 8).

License
=======

`libviska-java` is free software and is licensed under Apache-2.0 License. The
license is available in the `LICENSE` file.

Contributing
============

The team behind the Viska project is relatively small compared to its
proprietary counterparts who are backed by large companies. Therefore, we
welcome all kinds of help!

## How to Build

The default Gradle tasks are set to build the release-variant binaries and the
Javadoc.

This project also uses [Checker Framework](https://checkerframework.org) for
checking type errors. In order to enable it, run Gradle with a project property
`USE_CHECKER_FRAMEWORK`. For example:

```
./gradlew assemble -PUSE_CHECKER_FRAMEWORK=true
```