package chat.viska.xmpp;

/**
 * @since 0.1
 */
public class ConnectionMethodNotFoundException extends Exception {
  public ConnectionMethodNotFoundException() {
    super();
  }

  public ConnectionMethodNotFoundException(String s) {
    super(s);
  }

  public ConnectionMethodNotFoundException(String s, Throwable throwable) {
    super(s, throwable);
  }

  public ConnectionMethodNotFoundException(Throwable throwable) {
    super(throwable);
  }
}