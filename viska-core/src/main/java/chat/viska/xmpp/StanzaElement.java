package chat.viska.xmpp;

public interface StanzaElement {
  String getXmlns();
  String getLang();
  void setLang(String lang);
}