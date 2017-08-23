Notes for Developers
====================

Here are some notes of the library's implementation that give hints to 
developers of `libviska-java` instead of its users. Most of them involves
multiple components and therefore are not suitable to be put in the Javadoc of
any particular classes.

Chain Reaction When a Connection Is Being Closed
------------------------------------------------

There are various components inside a `DefaultSession` each of who maintains its 
own internal state. Here is a diagram illustrating how they communicate with
each other when an XMPP session is being disconnected. For convenience, it
assumes using a `NettyWebSocketSession`.

```text
+---------------------------+
|                           |
|   NettyWebSocketSession   |
|                           |
+---------------------------+

             +
             | triggers
             v

+---------------------------+                    +----------------+
|                           |    listened by     |                |
| ConnectionTerminatedEvent | +----------------> | DefaultSession |
|                           |                    |                |
+---------------------------+                    +----------------+

             +
             | listened
             |    by
             |
             v

+---------------------------+
|                           |
|      HandshakerPipe       |
|                           |
+---------------------------+

             +
             | publishes
             v

+---------------------------+                    +----------------+
|                           |    subscribed by   |                |
|   HandshakerPipe.State    | +----------------> |    Pipeline    |
|                           |                    |                |
+---------------------------+                    +----------------+
```

Whenever the `HandshakerPipe` detects the XML stream has closed while the 
network connection is still active, which means the connection is being closed 
by the client instead of terminated by the server, the `HandshakerPipe` asks the
`DefaultSession` to close the connection by invoking `disconnect()`, thus 
eliminating a loop chain.