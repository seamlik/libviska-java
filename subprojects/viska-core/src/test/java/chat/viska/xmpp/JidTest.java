package chat.viska.xmpp;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

public class JidTest {

  @Test
  public void parseJidTest() throws Exception {
    Assertions.assertArrayEquals(
        new String[] { "juliet", "example.com", ""},
        Jid.parseJidParts("juliet@example.com")
    );
    Assertions.assertArrayEquals(
        new String[] { "", "example.com", ""},
        Jid.parseJidParts("example.com")
    );
    Assertions.assertArrayEquals(
        new String[] { "", "example.com", "foo"},
        Jid.parseJidParts("example.com/foo")
    );
    Assertions.assertArrayEquals(
        new String[] { "juliet", "example.com", "foo@bar"},
        Jid.parseJidParts("juliet@example.com/foo@bar")
    );
    Assertions.assertArrayEquals(
        new String[] { "juliet", "example.com", "foo"},
        Jid.parseJidParts("<juliet@example.com/foo>")
    );
    Assertions.assertThrows(
        InvalidJidSyntaxException.class,
        new Executable() {
          @Override
          public void execute() throws Throwable {
            Jid.parseJidParts("@example.com");
          }
        }
    );
    Assertions.assertThrows(
        InvalidJidSyntaxException.class,
        new Executable() {
          @Override
          public void execute() throws Throwable {
            Jid.parseJidParts("@");
          }
        }
    );
    Assertions.assertThrows(
        InvalidJidSyntaxException.class,
        new Executable() {
          @Override
          public void execute() throws Throwable {
            Jid.parseJidParts("/");
          }
        }
    );
  }
}