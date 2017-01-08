package chat.viska.xmpp;

/**
 * @since 0.1
 */
public class DuplicatedExtensionsException extends Exception {

  public DuplicatedExtensionsException() {
  }

  public DuplicatedExtensionsException(String s) {
    super(s);
  }

  public DuplicatedExtensionsException(String s, Throwable throwable) {
    super(s, throwable);
  }

  public DuplicatedExtensionsException(Throwable throwable) {
    super(throwable);
  }

  public DuplicatedExtensionsException(String s, Throwable throwable, boolean b, boolean b1) {
    super(s, throwable, b, b1);
  }
}