package chat.viska.xmpp;

/**
 * @author Kai-Chung Yan (殷啟聰)
 * @since 0.1
 */
public class Contact implements SessionAware {

  private Account account;
  private String group;

  public String getGroup() {
    return group;
  }

  public void setGroup(String group) {

  }

  @Override
  public Session getSession() {
    return account.getSession();
  }
}