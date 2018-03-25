package chat.viska.xmpp;

import chat.viska.xmpp.plugins.base.BasePlugin;
import java.util.logging.Level;
import javax.annotation.Nonnull;

public class TestUtils {

  @Nonnull
  public static SimulatedSession requestSession() {
    final SimulatedSession session = new SimulatedSession();
    session.setNegotiatedJid(new Jid("user@example.com"));
    session.getPluginManager().apply(BasePlugin.class);
    session.getLogger().setLevel(Level.ALL);
    session.login();

    final BasePlugin basePlugin = session.getPluginManager().getPlugin(BasePlugin.class);
    basePlugin.softwareNameProperty().change("Viska");
    basePlugin.softwareVersionProperty().change("unknown");
    basePlugin.operatingSystemProperty().change(System.getProperty("os.name", "unknown"));
    return session;
  }

  private TestUtils() {}
}