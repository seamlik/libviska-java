libviska-java: XMPP Client Library for Android and Java SE
==========================================================

libviska-java is an XMPP library written in Java. It aims to support as many
modern or experimental features as possible. It is distinct from other
counterparts with the following reasons:

  * Supports VoIP calls using XMPP Jingle standards.
  * Rapidly drop features or standards that we consider out of fashion or
    outdated. We believe this can accelerate the evolution of XMPP standards.
  * Uses [SimpleXML](http://simple.sourceforge.net) to parse XMPP stanzas (for
    Android compatibility, otherwise we would use JAXB)
  * Designed for being used by both Java SE and Android applications.
  * Supports end-to-end encryption.

This library can be used in Android API Level >= 19 and Java SE >= 7.

License
-------

libviska-java is free software and is licensed under MIT License. The license is
available in the `LICENSE` file.

Contributing
------------

The devteam behind the Viska project is relatively small compared to its
proprietary counterparts who are backed by large companies. Therefore, we
welcome all kinds of help!

### How to Build

Simply run

```bash
gradle build
```

### Report Bugs

We use the issue tracker of GitHub and pull requests are welcomed.

### TODOs

* Supports video conferencing
* Supports text messaging
* Supports multimedia messaging