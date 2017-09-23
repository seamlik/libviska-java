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

package chat.viska.commons;

import java.io.InputStream;
import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.concurrent.ThreadSafe;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * Provides utility functions for working with DOM.
 */
@ThreadSafe
public class DomUtils {

  private static final DocumentBuilder DOM_BUILDER;
  private static final Transformer DOM_TRANSFORMER;

  static {
    final TransformerFactory transformerFactory = TransformerFactory.newInstance();
    final DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
    builderFactory.setIgnoringComments(true);
    builderFactory.setNamespaceAware(true);
    try {
      DOM_BUILDER = builderFactory.newDocumentBuilder();
      DOM_TRANSFORMER = transformerFactory.newTransformer();
    } catch (Exception ex) {
      throw new RuntimeException("This JVM does not support DOM.", ex);
    }
    DOM_TRANSFORMER.setOutputProperty(
        OutputKeys.OMIT_XML_DECLARATION,
        "yes"
    );
    DOM_TRANSFORMER.setOutputProperty(
        OutputKeys.INDENT,
        "no"
    );
  }

  private DomUtils() {}

  /**
   * Converts a {@link NodeList} to a {@link List}.
   */
  @Nonnull
  public static List<Node> convertToList(@Nonnull final NodeList nodeList) {
    final List<Node> list = new ArrayList<>(nodeList.getLength());
    for (int i = 0; i < nodeList.getLength(); ++i) {
      list.add(nodeList.item(i));
    }
    return list;
  }

  /**
   * Converts a {@link Document} to a {@link String}.
   */
  @Nonnull
  public static String writeString(@Nonnull final Document document)
      throws TransformerException {
    final Writer writer = new StringWriter();
    synchronized (DOM_TRANSFORMER) {
      DOM_TRANSFORMER.transform(new DOMSource(document), new StreamResult(writer));
    }
    return writer.toString();
  }

  /**
   * Reads a {@link Document} from a {@link String}.
   */
  @Nonnull
  public static Document readDocument(final String xml)
      throws SAXException {
    final Document document;
    synchronized (DOM_BUILDER) {
      try {
        document = DOM_BUILDER.parse(new InputSource(new StringReader(xml)));
      } catch (IOException ex) {
        throw new RuntimeException(ex);
      }
    }
    document.normalizeDocument();
    return document;
  }

  @Nonnull
  public static Document readDocument(final InputStream xml)
      throws SAXException, IOException {
    final Document document;
    synchronized (DOM_BUILDER) {
      document = DOM_BUILDER.parse(xml);
    }
    document.normalizeDocument();
    return document;
  }
}