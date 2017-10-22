package chat.viska.xmpp.plugins;

import chat.viska.xmpp.SimulatedSession;
import chat.viska.xmpp.TestUtils;
import io.reactivex.schedulers.Schedulers;
import java.util.Set;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class BasePluginTest {

  @Test
  public void queryDiscoInfoTest() throws Exception {
    final SimulatedSession session = TestUtils.requestSession();
    final BasePlugin plugin = session.getPluginManager().getPlugin(BasePlugin.class);
    final Set<String> features = session.getPluginManager().getAllFeatures();
    session
        .getOutboundStream()
        .observeOn(Schedulers.io())
        .take(2)
        .subscribe(session::readStanza);
    final DiscoInfo result = plugin.queryDiscoInfo(session.getNegotiatedJid()).blockingGet();
    Assertions.assertTrue(result.getFeatures().containsAll(features));
  }
}