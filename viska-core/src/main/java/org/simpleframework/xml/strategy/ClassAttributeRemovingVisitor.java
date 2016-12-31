package org.simpleframework.xml.strategy;

import org.simpleframework.xml.stream.InputNode;
import org.simpleframework.xml.stream.NodeMap;
import org.simpleframework.xml.stream.OutputNode;

/**
 * Simple XML writes a {@code class} attribute to Jingle's
 * {@code <description/>} and {@code <transport/>} and it can't be turned off!
 * Therefore we have to use this trick.
 */
public class ClassAttributeRemovingVisitor implements Visitor {

  @Override
  public void read(Type type, NodeMap<InputNode> node) throws Exception {}

  @Override
  public void write(Type type, NodeMap<OutputNode> node) throws Exception {
    String className = type.getType().getName();
    String prefix = "chat.viska.xmpp.stanzas.JingleInfoQuery$Jingle$Content$";
    if (className.startsWith(prefix)) {
      if (className.endsWith("Description") || className.endsWith("Transport")) {
        node.getNode().getAttributes().remove("class");
      }
    }
  }
}