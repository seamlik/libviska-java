package chat.viska.xmpp;

/**
 * @since 0.1
 */
public class ServerConnectionEndpointNotFoundException extends Exception {
  public ServerConnectionEndpointNotFoundException() {
    super();
  }

  public ServerConnectionEndpointNotFoundException(String s) {
    super(s);
  }

  public ServerConnectionEndpointNotFoundException(String s, Throwable throwable) {
    super(s, throwable);
  }

  public ServerConnectionEndpointNotFoundException(Throwable throwable) {
    super(throwable);
  }
}