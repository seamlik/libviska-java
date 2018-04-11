/*
 * Copyright 2018 Kai-Chung Yan (殷啟聰)
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

package chat.viska.xmpp.plugins.jingle;

import chat.viska.xmpp.CommonXmlns;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * <a href="https://xmpp.org/extensions/xep-0371.html">Jingle ICE transport method</a>.
 */
public class IceTransport implements Transport {

  /**
   * ICE candidate.
   */
  public static class Candidate {

    public final String foundation;
    public final String id;
    public final String ip;
    public final String protocol;
    public final String relAddr;
    public final String tcptype;
    public final String type;
    public final int generation;
    public final int port;
    public final int relPort;
    public final long priority;
    public final short component;

    /**
     * Default constructor.
     */
    public Candidate(final short component,
                     final String foundation,
                     final int generation,
                     final String id,
                     final String ip,
                     final int port,
                     final long priority,
                     final String protocol,
                     final String relAddr,
                     final int relPort,
                     final String tcptype,
                     final String type) {
      this.component = component;
      this.foundation = foundation;
      this.generation = generation;
      this.id = id;
      this.ip = ip;
      this.port = port;
      this.priority = priority;
      this.protocol = protocol;
      this.relAddr = relAddr;
      this.relPort = relPort;
      this.tcptype = tcptype;
      this.type = type;
    }
  }

  private final List<Candidate> candidates;
  private final String ufrag;
  private final String pwd;

  /**
   * Default constructor.
   */
  public IceTransport(final String ufrag, final String pwd, final List<Candidate> candidates) {
    this.ufrag = ufrag;
    this.pwd = pwd;
    this.candidates = Collections.unmodifiableList(new ArrayList<>(candidates));
  }

  public List<Candidate> getCandidates() {
    return candidates;
  }

  public String getPwd() {
    return pwd;
  }

  public String getUfrag() {
    return ufrag;
  }

  @Override
  public String getNamespace() {
    return CommonXmlns.JINGLE_ICE;
  }
}