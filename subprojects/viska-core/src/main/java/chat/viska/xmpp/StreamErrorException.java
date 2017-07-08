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

import io.reactivex.annotations.NonNull;
import io.reactivex.annotations.Nullable;
import java.util.Objects;
import org.apache.commons.lang3.Validate;

/**
 * Indicates a stream error has occurred.
 */
public class StreamErrorException extends Exception {

  /**
   * Cause of a stream error. These conditions are defined in
   * <a href="https://tools.ietf.org/html/rfc6120#section-4.9.3">RFC 6120 -
   * Extensible Messaging and Presence Protocol (XMPP): Core</a> and most of
   * their descriptions are copied from this specification.
   */
  public enum Condition {

    /**
     * Indicates invalid XML format.
     */
    BAD_FORMAT,

    /**
     * Indicates an entity has sent a namespace prefix that is unsupported, or
     * has sent no namespace prefix on an element that needs such a prefix.
     */
    BAD_NAMESPACE_PREFIX,

    CONFLICT,

    /**
     * Indicates a timeout during closing a stream.
     */
    CONNECTION_TIMEOUT,

    /**
     * Indicates the value of the {@code to} attribute provided in the initial
     * stream header corresponds to an FQDN that is no longer serviced by the
     * receiving entity.
     */
    HOST_GONE,

    /**
     * The value of the {@code to} attribute provided in the initial stream
     * header does not correspond to an FQDN that is serviced by the receiving
     * entity.
     */
    HOST_UNKNOWN,

    /**
     * Indicates a stanza sent between two servers lacks a {@code to} or
     * {@code from} attribute, the {@code to} or {@code from} attribute has no
     * value, or the value violates the rules for XMPP addresses.
     */
    IMPROPER_ADDRESSING,

    /**
     * Indicates the server has experienced a misconfiguration or other internal
     * error that prevents it from servicing the stream.
     */
    INTERNAL_SERVER_ERROR,

    /**
     * Indicates an invalid {@code from} attribute.
     */
    INVALID_FROM,

    /**
     * Indicates the XML namespace of the stream header or the stream itself is
     * invalid.
     */
    INVALID_NAMESPACE,

    /**
     * Indicates the XML data mismatches XMPP standards.
     */
    INVALID_XML,

    /**
     * Indicates an entity has attempted to send XML data before the stream is
     * authenticated, or the
     * <a href="https://datatracker.ietf.org/doc/rfc4422">SASL</a>
     * authentication fails.
     */
    NOT_AUTHORIZED,

    /**
     * Indicates an entity has sent XML that is not namespace-well-formed.
     */
    NOT_WELL_FORMED,

    /**
     * Indicates an entity has violated some local service policy.
     */
    POLICY_VIOLATION,

    /**
     * Indicates the server is unable to properly connect to a remote entity
     * that is needed for authentication or authorization
     */
    REMOTE_CONNECTION_FAILED,

    /**
     * Indicates the server is closing the stream because it has new (typically
     * security-critical) features to offer.
     */
    RESET,

    /**
     * Indicates the server lacks the system resources necessary to service the
     * stream.
     */
    RESOURCE_CONSTRAINT,

    /**
     * Indicates an entity has attempted to send restricted XML features such as
     * a comment, processing instruction, DTD subset, or XML entity reference.
     */
    RESTRICTED_XML,

    /**
     * Indicates the server will not provide service to the initiating entity
     * but is redirecting traffic to another host under the administrative
     * control of the same service provider.
     */
    SEE_OTHER_HOST,

    /**
     * Indicates the server is being shut down and all active streams are being
     * closed.
     */
    SYSTEM_SHUTDOWN,

    /**
     * Indicates an error condition which is not defined by the specification.
     */
    UNDEFINED_CONDITION,

    /**
     * Indicates an entity has encoded the stream in an unsupported encoding.
     * UTF-8 is the only supported encoding in the current specifications.
     */
    UNSUPPORTED_ENCODING,

    /**
     * Indicates the receiving entity has advertised a mandatory-to-negotiate
     * stream feature that the initiating entity does not support, and has
     * offered no other mandatory-to-negotiate feature alongside the unsupported
     * feature.
     */
    UNSUPPORTED_FEATURE,

    /**
     * Indicates an entity has sent a first-level child of the stream that is
     * not supported.
     */
    UNSUPPORTED_STANZA_TYPE,

    /**
     * Indicates the {@code version} attribute provided by the initiating entity
     * in the stream header specifies a version of XMPP that is not supported by
     * the receiving entity.
     */
    UNSUPPORTED_VERSION;

    /**
     * Returns a constant of {@link Condition} based on the XML tag name of the
     * stream error.
     * @param name The XML tag name of the stream error.
     * @return {@code null} If the specified enum type has no constant with the
     *         specified name
     */
    @Nullable
    public static Condition of(@NonNull final String name) {
      try {
        return Enum.valueOf(
            Condition.class,
            name.replace('-', '_').toUpperCase()
        );
      } catch (Exception ex) {
        return null;
      }
    }

    @Override
    public String toString() {
      return name().replace('_', '-').toLowerCase();
    }
  }

  private final Condition condition;

  public StreamErrorException(final @NonNull Condition condition) {
    Objects.requireNonNull(condition, "`condition` is absent.");
    this.condition = condition;
  }

  public StreamErrorException(@NonNull final Condition condition,
                              @NonNull final String text,
                              @NonNull final Throwable throwable) {
    super(text, throwable);
    Objects.requireNonNull(condition, "`condition` is absent.");
    this.condition = condition;
  }

  public StreamErrorException(@NonNull final Condition condition,
                              @NonNull final Throwable throwable) {
    super(throwable);
    Objects.requireNonNull(condition, "`condition` is absent.");
    this.condition = condition;
  }

  public StreamErrorException(@NonNull final Condition condition,
                              @NonNull final String text) {
    super(text);
    Objects.requireNonNull(condition, "`condition` is absent.");
    this.condition = condition;
  }

  @NonNull
  public Condition getCondition() {
    return condition;
  }
}