package chat.viska.xmpp;

/**
 * @since 0.1
 */
public class Contact implements SessionAware {

  private Account account;
  private String group;
  private Jid jid;

  public String getGroup() {
    return group;
  }

  public void setGroup(String group) {

  }

  public void remove() {

  }

  @Override
  public Session getSession() {
    return account.getSession();
  }
}