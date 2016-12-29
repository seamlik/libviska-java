/**
 * Provides classes modeling XMPP stanzas.
 * <p>
 *   The classes of this package merely represent XMPP stanzas and do not have
 *   any functionality. Therefore all classes in this package are immutable
 *   types and can be considered thread safe.
 * </p>
 * <p>
 *   When XML documents are being transformed as objects of classes in this
 *   package, validation is not performed and it is the duty of the logic of a
 *   running XMPP session. However, their public constructors are responsible
 *   for checking which attributes or elements are mandatory. If not explicitly
 *   noted, the constructor arguments are usually optional. Please consult the
 *   XMPP specifications for more details.
 * </p>
 * @since 0.1
 */
package chat.viska.xmpp.stanzas;