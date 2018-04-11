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

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.concurrent.ThreadSafe;
import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.checkerframework.checker.lock.qual.GuardedBy;
import org.checkerframework.dataflow.qual.Pure;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * Provides utility functions for working with DOM.
 */
@ThreadSafe
public final class DomUtils {

  @GuardedBy("itself")
  private static final DocumentBuilder BUILDER;

  @GuardedBy("itself")
  private static final Transformer TRANSFORMER;

  @GuardedBy("itself")
  private static final XPath XPATH = XPathFactory.newInstance().newXPath();

  static {
    final TransformerFactory transformerFactory = TransformerFactory.newInstance();
    final DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
    builderFactory.setIgnoringComments(true);
    builderFactory.setNamespaceAware(true);
    try {
      BUILDER = builderFactory.newDocumentBuilder();
      TRANSFORMER = transformerFactory.newTransformer();
    } catch (Exception ex) {
      throw new RuntimeException("This JVM does not support DOM.", ex);
    }
    TRANSFORMER.setOutputProperty(
        OutputKeys.OMIT_XML_DECLARATION,
        "yes"
    );
    TRANSFORMER.setOutputProperty(
        OutputKeys.INDENT,
        "no"
    );
  }

  private DomUtils() {}

  /**
   * Converts a {@link NodeList} to a {@link List}.
   */
  @Pure
  public static List<Node> convertToList(final NodeList nodeList) {
    final List<Node> list = new ArrayList<>(nodeList.getLength());
    int i = 0;
    while (nodeList.item(i) != null) {
      list.add(nodeList.item(i));
      ++i;
    }
    return list;
  }

  /**
   * Converts a {@link Document} to a {@link String}.
   */
  public static String writeString(final Document document)
      throws TransformerException {
    final Writer writer = new StringWriter();
    synchronized (TRANSFORMER) {
      TRANSFORMER.transform(new DOMSource(document), new StreamResult(writer));
    }
    return writer.toString();
  }

  /**
   * Reads a {@link Document}.
   */
  public static Document readDocument(final String xml)
      throws SAXException {
    synchronized (BUILDER) {
      try {
        final Document document = BUILDER.parse(new InputSource(new StringReader(xml)));
        document.normalizeDocument();
        return document;
      } catch (IOException ex) {
        throw new RuntimeException(ex);
      }
    }

  }

  /**
   * Reads a {@link Document}.
   */
  public static Document readDocument(final InputStream xml)
      throws SAXException, IOException {
    final Document document;
    synchronized (BUILDER) {
      document = BUILDER.parse(xml);
    }
    document.normalizeDocument();
    return document;
  }

  public static Object evaluateXPath(final String expression, final Object item, final QName type)
      throws XPathExpressionException {
    synchronized (XPATH) {
      return XPATH.evaluate(expression, item, type);
    }
  }
}