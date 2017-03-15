package chat.viska.xmpp;

/**
 * @since 0.1
 */
public class ConnectionEndpointNotFoundException extends Exception {
  public ConnectionEndpointNotFoundException() {
    super();
  }

  public ConnectionEndpointNotFoundException(String s) {
    super(s);
  }

  public ConnectionEndpointNotFoundException(String s, Throwable throwable) {
    super(s, throwable);
  }

  public ConnectionEndpointNotFoundException(Throwable throwable) {
    super(throwable);
  }
}