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
import java.net.URI;
import java.util.List;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;

public class Cmd {

  @Parameters(commandNames = "disco-info")
  private class InfoCommand {

    @Parameter(converter = JidConverter.class)
    private List<Jid> entities;

    private void run() throws Throwable {
      Session session = null;
      try {
        session = new NettyWebSocketSession(
            jid,
            new Connection(Connection.Protocol.WEBSOCKET, websocket)
        );
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
          final DiscoInfo result = entity.queryDiscoInfo().blockingGet();
          System.out.println("[" + it + "]");
          System.out.println("  Features: ");
          result.getFeatures()
              .stream()
              .sorted()
              .forEach(item -> System.out.println("    " + item));
          result.getIdentities()
              .forEach(identity -> {
                System.out.println("  Identity:");
                System.out.println("    Category: " + identity.getCategory());
                System.out.println("    Type: " + identity.getType());
                System.out.println("    Name: " + identity.getName());
              });
        }
      } catch (Exception ex) {
        if (debug) {
          throw ex;
        } else {
          System.err.println("Login failed.");
          ex.printStackTrace();
        }
      } finally {
        if (session != null) {
          session.close();
        }
      }
    }
  }

  @Parameter(names = "--jid", converter = JidConverter.class, required = true)
  private Jid jid;

  @Parameter(names = "--password", password = true, required = true)
  private String password;

  @Parameter(names = "--websocket", converter = URIConverter.class)
  private URI websocket;

  @Parameter(names = "--debug")
  private boolean debug;

  @Parameter(names = { "-h", "--help", "help" }, help = true)
  private boolean help;

  public static void main(String[] args) throws Throwable {
    new Cmd().run(args);
  }

  private void run(String... args) throws Throwable {
    InfoCommand infoCommand = new InfoCommand();

    JCommander jCommander = JCommander
        .newBuilder()
        .programName("viska-cmd-java")
        .addObject(this)
        .addCommand(infoCommand)
        .build();
    if (args.length == 0) {
      jCommander.usage();
      return;
    }
    jCommander.parse(args);
    if (help) {
      jCommander.usage();
      return;
    }
    switch (jCommander.getParsedCommand()) {
      case "info":
        infoCommand.run();
        break;
      default:
        System.err.println("Wrong command.");
        break;
    }
  }
}