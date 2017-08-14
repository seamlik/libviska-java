/*
 * Copyright 2017 Kai-Chung Yan (殷啟聰)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package chat.viska.cmd;

import chat.viska.cmd.jcommander.JidConverter;
import chat.viska.xmpp.AbstractEntity;
import chat.viska.xmpp.BasePlugin;
import chat.viska.xmpp.Connection;
import chat.viska.xmpp.DiscoInfo;
import chat.viska.xmpp.Jid;
import chat.viska.xmpp.NettyWebSocketSession;
import chat.viska.xmpp.Session;
import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.beust.jcommander.converters.URIConverter;
import io.reactivex.Observable;
import java.net.URI;
import java.util.List;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;

public class Cmd {

  @Parameters(commandNames = "info")
  private class InfoCommand {

    @Parameter(converter = JidConverter.class)
    private List<Jid> entities;

    private void run() throws Throwable {
      try (final Session session = new NettyWebSocketSession(jid, connection)) {
        if (debug) {
          final Handler handler = new ConsoleHandler();
          handler.setLevel(Level.ALL);
          session.getLogger().addHandler(handler);
          session.getLogger().setLevel(Level.ALL);
          session.getLogger().setUseParentHandlers(false);
        }
        session.login(password).blockingAwait();
        final BasePlugin basePlugin = (BasePlugin) session
            .getPluginManager()
            .getPlugin(BasePlugin.class);
        for (Jid it : entities) {
          final AbstractEntity entity = basePlugin.getXmppEntityInstance(it);
          final DiscoInfo discoInfo = entity.queryDiscoInfo().blockingGet();
          final List<AbstractEntity.Item> items = entity.queryItems(null).blockingGet();
          System.out.println("<" + it + ">");
          System.out.println("  Features: ");
          discoInfo
              .getFeatures()
              .stream()
              .sorted()
              .forEach(item -> System.out.println("    " + item));
          discoInfo.getIdentities().forEach(identity -> {
            System.out.println("  Identity:");
            System.out.println("    Category: " + identity.getCategory());
            System.out.println("    Type: " + identity.getType());
            System.out.println("    Name: " + identity.getName());
          });
          System.out.println("  Items: ");
          items.forEach(item -> {
            System.out.println("    JID: " + item.getJid());
            System.out.println("    Name: " + item.getName());
            System.out.println("    Node: " + item.getNode());
          });
        }
      }
    }
  }

  @Parameter(names = "--jid", description = "JID", converter = JidConverter.class, required = true)
  private Jid jid;

  @Parameter(names = "--password", description = "Password", password = true, required = true)
  private String password;

  @Parameter(names = "--websocket", description = "WebSocket URI", converter = URIConverter.class)
  private URI websocket;

  @Parameter(names = "--debug", description = "Display debug infomation")
  private boolean debug;

  @Parameter(names = { "-h", "--help", "help" }, description = "Display help", help = true)
  private boolean help;

  private Connection connection;

  public static void main(String[] args) throws Throwable {
    new Cmd().run(args);
  }

  private void initialize() {
    if (websocket != null) {
      connection = new Connection(Connection.Protocol.WEBSOCKET, websocket);
    } else {
      connection = Observable
          .fromIterable(Connection.queryDns(jid.getDomainPart()).blockingGet())
          .filter(it -> it.getProtocol() == Connection.Protocol.WEBSOCKET)
          .filter(Connection::isTlsEnabled)
          .blockingFirst();
    }
  }

  private void run(String... args) throws Throwable {
    InfoCommand infoCommand = new InfoCommand();
    JCommander jcommander = JCommander
        .newBuilder()
        .programName("viska-cmd-java")
        .addObject(this)
        .addCommand(infoCommand)
        .build();
    if (args.length == 0) {
      jcommander.usage();
      return;
    }
    jcommander.parse(args);
    if (help) {
      jcommander.usage();
      return;
    }
    try {
      switch (jcommander.getParsedCommand()) {
        case "info":
          initialize();
          infoCommand.run();
          break;
        default:
          jcommander.usage();
          break;
      }
    } catch (Exception ex) {
      if (debug) {
        throw ex;
      } else {
        ex.printStackTrace();
      }
    }
  }
}