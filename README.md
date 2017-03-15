libviska-java: XMPP Client Library for Android and Java SE
==========================================================

libviska-java is an XMPP library written in Java. It aims to support as many
modern or experimental features as possible. It is distinct from other
counterparts with the following reasons:

  * Supports VoIP calls using XMPP Jingle standards.
  * Rapidly drop features or standards that we consider out of fashion or
    outdated. We believe this can accelerate the evolution of XMPP standards.
  * Designed for being used by both Java SE and Android applications.
  * Supports end-to-end encryption.

This library can be used in Android API Level >= 19 and Java SE >= 7.

Supported XEPs
--------------

  * [XEP-0166: Jingle](https://xmpp.org/extensions/xep-0166.html)
  * [XEP-0343: Signaling WebRTC datachannels in Jingle](https://xmpp.org/extensions/xep-0343.html)

License
-------

libviska-java is free software and is licensed under Apache-2.0 License. The
license is available in the `LICENSE` file.

Contributing
------------

The devteam behind the Viska project is relatively small compared to its
proprietary counterparts who are backed by large companies. Therefore, we
welcome all kinds of help!

### How to Build

Simply run

```bash
gradle
```

### TODOs

* Supports video conferencing
* Supports text messaging over OMEMO
* Supports multimedia messaging