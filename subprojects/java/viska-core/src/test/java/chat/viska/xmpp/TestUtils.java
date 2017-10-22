package chat.viska.xmpp;

import chat.viska.xmpp.plugins.BasePlugin;
import javax.annotation.Nonnull;

public class TestUtils {

  @Nonnull
  public static SimulatedSession requestSession() {
    final SimulatedSession session = new SimulatedSession();
    session.setNegotiatedJid(new Jid("user@example.com"));
    session.getPluginManager().apply(BasePlugin.class);
    session.changeState(Session.State.ONLINE);

    final BasePlugin basePlugin = session.getPluginManager().getPlugin(BasePlugin.class);
    basePlugin.getSoftwareName().changeValue("Viska");
    basePlugin.getSoftwareVersion().changeValue("unknown");
    return session;
  }

  private TestUtils() {}
}