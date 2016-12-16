package viska.xmpp;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class JidTest {

  @Test
  public void parseJidTest() throws Exception {
    Assertions.assertArrayEquals(
        new String[] { "juliet", "example.com", ""},
        Jid.parse("juliet@example.com")
    );
    Assertions.assertArrayEquals(
        new String[] { "", "example.com", ""},
        Jid.parse("example.com")
    );
    Assertions.assertArrayEquals(
        new String[] { "", "example.com", "foo"},
        Jid.parse("example.com/foo")
    );
    Assertions.assertArrayEquals(
        new String[] { "juliet", "example.com", "foo@bar"},
        Jid.parse("juliet@example.com/foo@bar")
    );
    Assertions.assertArrayEquals(
        new String[] { "juliet", "example.com", "foo"},
        Jid.parse("<juliet@example.com/foo>")
    );
    Assertions.assertThrows(
        InvalidJidSyntaxException.class,
        () -> Jid.parse("@example.com")
    );
    Assertions.assertThrows(
        InvalidJidSyntaxException.class,
        () -> Jid.parse("@")
    );
    Assertions.assertThrows(
        InvalidJidSyntaxException.class,
        () -> Jid.parse("/")
    );
  }
}