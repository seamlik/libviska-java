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

package chat.viska.xmpp.plugins;

import chat.viska.commons.DomUtils;
import chat.viska.commons.EnumUtils;
import chat.viska.xmpp.CommonXmlns;
import chat.viska.xmpp.Jid;
import chat.viska.xmpp.Plugin;
import chat.viska.xmpp.Session;
import chat.viska.xmpp.Stanza;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.annotations.NonNull;
import java.util.AbstractMap;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

public class RosterPlugin implements Plugin {

  private static final Set<Map.Entry<String, String>> supportedIqs = Collections.singleton(
      new AbstractMap.SimpleImmutableEntry<>(CommonXmlns.ROSTER, "query")
  );

  private final Session session;
  private final Roster roster;

  private static List<? extends Roster.Item> convertToRosterItems(@NonNull final Document xml) {
    final Element queryElement = (Element) xml
        .getDocumentElement()
        .getElementsByTagNameNS(CommonXmlns.ROSTER, "query")
        .item(0);
    return Observable.fromIterable(
        DomUtils.toList(queryElement.getElementsByTagName("item"))
    ).cast(Element.class).map(it -> new CachedRosterItem(
        new Jid(it.getAttribute("jid")),
        EnumUtils.fromXmlValue(
            Roster.Item.Subscription.class,
            it.getAttribute("subscription")
        ),
        it.getAttribute("name"),
        Observable
            .fromIterable(DomUtils.toList(it.getElementsByTagName("group")))
            .map(Node::getTextContent)
            .toList().blockingGet()
    )).toList().blockingGet();
  }

  public RosterPlugin(@NonNull final Session session) {
    this.session = session;
    this.roster = new Roster(session);
  }

  @NonNull
  public Roster getRoster() {
    return roster;
  }

  @NonNull
  public Maybe<List<? extends Roster.Item>> queryRoster() {
    try {
      return this.session
          .query(CommonXmlns.ROSTER, null, null)
          .getResponse()
          .map(Stanza::getDocument)
          .map(RosterPlugin::convertToRosterItems);
    } catch (SAXException ex) {
      throw new RuntimeException(ex);
    }
  }

  @NonNull
  public Maybe<List<? extends Roster.Item>>
  queryRoster(@NonNull final String version,
              @NonNull final Collection<Roster.Item> cached) {
    final Map<String, String> param = new HashMap<>();
    param.put("ver", version);
    throw new UnsupportedOperationException();
  }

  @Override
  public Set<Class<? extends Plugin>> getDependencies() {
    return Collections.emptySet();
  }

  @Override
  public Set<String> getFeatures() {
    return Collections.emptySet();
  }

  @Override
  public Set<Map.Entry<String, String>> getSupportedIqs() {
    return supportedIqs;
  }

  @Override
  public Session getSession() {
    return session;
  }
}