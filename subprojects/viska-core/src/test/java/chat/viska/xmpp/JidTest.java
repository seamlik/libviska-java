package chat.viska.xmpp;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

public class JidTest {

  @Test
  public void parseJidTest() throws Exception {
    new Jid("juliet@example.com");
    new Jid("example.com");
    new Jid("example.com/foo");
    new Jid("juliet@example.com/foo@bar");
    Assertions.assertThrows(
        InvalidJidSyntaxException.class,
        () -> new Jid("@example.com")
    );
    Assertions.assertThrows(
        InvalidJidSyntaxException.class,
        () -> new Jid("@")
    );
    Assertions.assertThrows(
        InvalidJidSyntaxException.class,
        () -> new Jid("/")
    );
  }
}