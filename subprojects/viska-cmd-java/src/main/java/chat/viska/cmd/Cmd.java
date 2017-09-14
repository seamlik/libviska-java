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

import chat.viska.commons.EnumUtils;
import chat.viska.xmpp.Connection;
import chat.viska.xmpp.Jid;
import chat.viska.xmpp.NettyTcpSession;
import chat.viska.xmpp.NettyWebSocketSession;
import chat.viska.xmpp.Session;
import chat.viska.xmpp.plugins.BasePlugin;
import chat.viska.xmpp.plugins.DiscoInfo;
import chat.viska.xmpp.plugins.DiscoItem;
import chat.viska.xmpp.plugins.RosterItem;
import chat.viska.xmpp.plugins.SoftwareInfo;
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

    @Parameter(converter = JidConverter.class, required = true)
    private List<Jid> entities;

    private void run() throws Throwable {
      final BasePlugin basePlugin = (BasePlugin) session
          .getPluginManager()
          .getPlugin(BasePlugin.class);
      for (Jid it : entities) {
        System.out.println("<" + it + ">");
        final DiscoInfo discoInfo = basePlugin.queryDiscoInfo(it).blockingGet();
        System.out.println("  Features: ");
        discoInfo.getFeatures().stream().sorted().forEach(
            item -> System.out.println("    " + item)
        );
        System.out.println("  Identities:");
        discoInfo.getIdentities().forEach(identity -> {
          System.out.println("    Name: " + identity.getName());
          System.out.println("      Category: " + identity.getCategory());
          System.out.println("      Type: " + identity.getType());
        });

        final List<DiscoItem> items = basePlugin.queryDiscoItems(it, null).blockingGet();
        System.out.println("  Items: ");
        items.forEach(item -> {
          System.out.println("    JID: " + item.getJid());
          if (!item.getName().isEmpty()) {
            System.out.println("      Name: " + item.getName());
          }
          if (!item.getNode().isEmpty()) {
            System.out.println("      Node: " + item.getNode());
          }
        });

        if (it.getResourcePart().isEmpty()) {
          return;
        }
        final SoftwareInfo softwareInfo = basePlugin.querySoftwareInfo(it).blockingGet();
        System.out.println("  Software Information: ");
        System.out.println("    Name: " + softwareInfo.getName());
        System.out.println("    Version: " + softwareInfo.getVersion());
        System.out.println("    Operating system: " + softwareInfo.getOperatingSystem());
      }
    }
  }

  @Parameters(commandNames = "roster-get")
  private class RosterGetCommand {

    private void run() throws Exception {
      final BasePlugin plugin = (BasePlugin)
          session.getPluginManager().getPlugin(BasePlugin.class);
      List<RosterItem> roster = plugin.queryRoster().blockingGet();
      roster.forEach(it -> {
        System.out.println("Jid: " + it.getJid());
        if (it.getSubscription() != null) {
          System.out.println(
              "  Subscription: " + EnumUtils.toXmlValue(it.getSubscription())
          );
        }
        if (!it.getName().isEmpty()) {
          System.out.println("  Name: " + it.getName());
        }
        if (!it.getGroups().isEmpty()) {
          System.out.println("  Groups:");
          it.getGroups().forEach(group -> System.out.println("    " + it));
        }
      });
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

  private Session session;

  public static void main(String[] args) throws Throwable {
    new Cmd().run(args);
  }

  private void initialize() {
    Connection connection;
    if (this.websocket != null) {
      connection = new Connection(Connection.Protocol.WEBSOCKET, this.websocket);
    } else {
      try {
        connection = Observable
            .fromIterable(Connection.queryDns(jid.getDomainPart()).blockingGet())
            .filter(Connection::isTlsEnabled)
            .blockingFirst();
      } catch (Exception ex) {
        throw new IllegalArgumentException(
            "No idea how to connect to this server.",
            ex
        );
      }
    }
    if (connection.getProtocol() == Connection.Protocol.WEBSOCKET) {
      this.session = new NettyWebSocketSession(this.jid, connection);
    } else {
      this.session = new NettyTcpSession(this.jid, null, connection, false);
    }

    this.session.getLogger().setUseParentHandlers(false);
    final Handler handler = new ConsoleHandler();
    handler.setLevel(Level.ALL);
    this.session.getLogger().addHandler(handler);
    if (this.debug) {
      this.session.getLogger().setLevel(Level.ALL);
    } else {
      this.session.getLogger().setLevel(Level.WARNING);
    }

    this.session.login(this.password).blockingAwait();
  }

  private void run(String... args) throws Throwable {
    InfoCommand infoCommand = new InfoCommand();
    RosterGetCommand rosterGetCommand = new RosterGetCommand();
    JCommander jcommander = JCommander
        .newBuilder()
        .programName("viska-cmd-java")
        .addObject(this)
        .addCommand(infoCommand)
        .addCommand(rosterGetCommand)
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
        case "roster-get":
          initialize();
          rosterGetCommand.run();
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
    } finally {
      if (this.session != null) {
        this.session.close();
      }
    }
  }
}