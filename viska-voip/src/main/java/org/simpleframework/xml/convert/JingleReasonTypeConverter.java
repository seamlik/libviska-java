package org.simpleframework.xml.convert;

import chat.viska.xmpp.stanzas.JingleInfoQuery;
import org.simpleframework.xml.stream.InputNode;
import org.simpleframework.xml.stream.OutputNode;

public class JingleReasonTypeConverter
    implements Converter<JingleInfoQuery.Jingle.Reason.ReasonType> {

  @Override
  public JingleInfoQuery.Jingle.Reason.ReasonType read(InputNode node)
      throws Exception {
    return Enum.valueOf(
      JingleInfoQuery.Jingle.Reason.ReasonType.class,
      node.getName().toUpperCase().replace('-', '_')
    );
  }

  @Override
  public void write(OutputNode node,
                    JingleInfoQuery.Jingle.Reason.ReasonType value)
      throws Exception {
    node.setName(value.name().toLowerCase().replace('_', '-'));
  }
}