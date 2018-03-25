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

import chat.viska.xmpp.Connection;
import chat.viska.xmpp.Jid;
import chat.viska.xmpp.Session;
import chat.viska.xmpp.StandardSession;
import chat.viska.xmpp.plugins.BasePlugin;
import chat.viska.xmpp.plugins.DiscoInfo;
import chat.viska.xmpp.plugins.DiscoItem;
import chat.viska.xmpp.plugins.RosterItem;
import chat.viska.xmpp.plugins.SoftwareInfo;
import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.beust.jcommander.converters.URIConverter;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import org.checkerframework.checker.nullness.qual.EnsuresNonNull;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

public class Cmd {

  @Parameters(commandNames = "info")
  private class InfoCommand {

    @Parameter(converter = JidConverter.class, required = true)
    private List<Jid> entities = Collections.emptyList();

    public void run() {
      initialize();
      final BasePlugin basePlugin = session.getPluginManager().getPlugin(BasePlugin.class);
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

        final List<DiscoItem> items = basePlugin.queryDiscoItems(it, "").blockingGet();
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
        session.close();
      }
    }
  }

  @Parameters(commandNames = "roster")
  private class RosterCommand {

    public void run() {
      initialize();
      final BasePlugin plugin = session.getPluginManager().getPlugin(BasePlugin.class);
      List<RosterItem> roster = plugin.queryRoster().blockingGet();
      roster.forEach(it -> {
        System.out.println("Jid: " + it.getJid());
        if (it.getSubscription() != null) {
          System.out.println(
              "  Subscription: " + it.getSubscription()
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

  @Parameters(commandNames = "connections")
  private class ConnectionsCommand {

    @Parameter(required = true)
    private List<String> domains = Collections.emptyList();

    public void run() {
      for (String domain : domains) {
        System.out.println('<' + domain + '>');
        final List<Connection> connections = Connection
            .queryAll(domain, null, Collections.emptyList())
            .blockingGet();
        for (Connection it : connections) {
          System.out.print(it.getProtocol());
          System.out.println(':');
          switch (it.getProtocol()) {
            case TCP:
              System.out.print("    ");
              System.out.print("Domain: ");
              System.out.println(it.getDomain());
              System.out.print("    ");
              System.out.print("Port: ");
              System.out.println(it.getPort());
              System.out.print("    ");
              System.out.print("TLS: ");
              System.out.println(it.getTlsMethod());
              break;
            case WEBSOCKET:
              System.out.print("    ");
              System.out.print("URI: ");
              System.out.println(it.getUri());
              break;
            default:
              break;
          }
        }
      }


    }
  }

  @Parameter(names = "--jid", description = "JID", converter = JidConverter.class)
  private Jid jid = Jid.EMPTY;

  @Parameter(names = "--password", description = "Password", password = true)
  private String password = "";

  @Parameter(names = "--websocket", description = "WebSocket URI", converter = URIConverter.class)
  private @Nullable URI websocket;

  @Parameter(names = "--debug", description = "Display debug infomation")
  private boolean debug;

  @Parameter(names = { "-h", "--help", "help" }, description = "Display help", help = true)
  private boolean help;

  private @MonotonicNonNull StandardSession session;

  public static void main(String[] args) throws Throwable {
    new Cmd().run(args);
  }

  @EnsuresNonNull("session")
  private void initialize() {
    final Connection connection;
    if (this.websocket != null) {
      connection = new Connection(Connection.Protocol.WEBSOCKET, this.websocket);
    } else {
      try {
        connection = Connection
            .queryAll(jid.getDomainPart(), null, Collections.emptyList())
            .flattenAsObservable(it -> it)
            .filter(Connection::isTlsEnabled)
            .blockingFirst();
      } catch (Exception ex) {
        throw new IllegalArgumentException(
            "No idea how to connect to this server.",
            ex
        );
      }
    }
    try {
      session = StandardSession.newInstance(Collections.singleton(connection.getProtocol()));
    } catch (Exception ex) {
      throw new RuntimeException("No XMPP Session implementation is installed.");
    }

    this.session.setLoginJid(this.jid);
    this.session.setConnection(connection);
    this.session.getPluginManager().apply(BasePlugin.class);

    this.session.getLogger().setUseParentHandlers(false);
    final Handler handler = new ConsoleHandler();
    handler.setLevel(Level.ALL);
    this.session.getLogger().addHandler(handler);
    if (this.debug) {
      this.session.getLogger().setLevel(Level.ALL);
    } else {
      this.session.getLogger().setLevel(Level.WARNING);
    }

    session.login(this.password);
    session
        .stateProperty()
        .getStream()
        .filter(it -> it == Session.State.ONLINE)
        .firstOrError()
        .blockingGet();
  }

  private void run(String... args) {
    final InfoCommand infoCommand = new InfoCommand();
    final RosterCommand rosterCommand = new RosterCommand();
    final ConnectionsCommand connectionsCommand = new ConnectionsCommand();
    JCommander jcommander = JCommander
        .newBuilder()
        .programName("viska-cmd-java")
        .addObject(this)
        .addCommand(infoCommand)
        .addCommand(rosterCommand)
        .addCommand(connectionsCommand)
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
          infoCommand.run();
          break;
        case "roster":
          rosterCommand.run();
          break;
        case "connections":
          connectionsCommand.run();
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