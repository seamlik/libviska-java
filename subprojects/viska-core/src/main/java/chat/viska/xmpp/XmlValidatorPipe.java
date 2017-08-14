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

package chat.viska.xmpp;

import chat.viska.commons.pipelines.BlankPipe;
import chat.viska.commons.pipelines.Pipeline;
import io.reactivex.annotations.NonNull;
import java.util.List;
import org.w3c.dom.Document;

class XmlValidatorPipe extends BlankPipe {

  public static class ValidationException extends Exception {

    public ValidationException(@NonNull final StreamErrorException cause) {
      super(cause);
    }
  }

  private void validateStream(@NonNull final Document document)
      throws ValidationException {
  }

  private void validateStanza(@NonNull final Document document)
      throws ValidationException {
    final String rootNs = document.getDocumentElement().getNamespaceURI();
    if (!CommonXmlns.STANZA_CLIENT.equals(rootNs)
        && !CommonXmlns.STANZA_SERVER.equals(rootNs)) {
      throw new ValidationException(new StreamErrorException(
          StreamErrorException.Condition.INVALID_XML,
          "Incorrect stanza namespace."
      ));
    }
  }

  @Override
  public void onReading(final Pipeline<?, ?> pipeline,
                        final Object toRead,
                        final List<Object> toForward) throws Exception {
    if (!(toRead instanceof Document)) {
      super.onReading(pipeline, toRead, toForward);
      return;
    }
    final Document document = (Document) toRead;
    final String rootName = document.getDocumentElement().getLocalName();
    if (Stanza.isStanza(document)) {
      validateStanza(document);
    } else {
      validateStream((Document) toRead);
    }
    super.onReading(pipeline, toRead, toForward);
  }
}