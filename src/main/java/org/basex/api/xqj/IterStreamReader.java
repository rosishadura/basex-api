package org.basex.api.xqj;

import java.util.NoSuchElementException;
import java.util.Properties;
import javax.xml.namespace.NamespaceContext;
import javax.xml.namespace.QName;
import javax.xml.stream.Location;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import org.basex.api.jaxp.BXNamespaceContext;
import org.basex.data.Data;
import org.basex.io.IO;
import org.basex.query.QueryException;
import org.basex.query.item.DBNode;
import org.basex.query.item.FNode;
import org.basex.query.item.Item;
import org.basex.query.item.Nod;
import org.basex.query.item.QNm;
import org.basex.query.item.Type;
import org.basex.query.iter.Iter;
import org.basex.query.iter.NodIter;
import org.basex.query.iter.NodeIter;
import org.basex.query.util.Namespaces;
import org.basex.util.TokenBuilder;
import org.basex.util.Util;
import static org.basex.util.Token.*;

/**
 * XML Stream Reader implementation.
 *
 * @author BaseX Team 2005-11, BSD License
 * @author Christian Gruen
 */
final class IterStreamReader implements XMLStreamReader {
  /** Properties. */
  private static final Properties PROPS = new Properties();
  /** Namespaces references. */
  private final Namespaces ns = new Namespaces();
  /** Current state. */
  int kind = START_DOCUMENT;
  /** Current item. */
  Item item;
  /** Result iterator. */
  private final Iter result;
  /** Next flag. */
  private boolean next;
  /** Node iterator. */
  private NodeReader read;
  /** Attributes. */
  private NodIter atts;

  /**
   * Constructor.
   * @param res result iterator
   */
  IterStreamReader(final Iter res) {
    result = res;
    // included for wrapping the stream reader into an XML event reader
    PROPS.put(XMLInputFactory.IS_NAMESPACE_AWARE, Boolean.FALSE);
  }

  @Override
  public void close() {
  }

  @Override
  public int getAttributeCount() {
    getAttributes();
    return (int) atts.size();
  }

  @Override
  public String getAttributeLocalName(final int i) {
    getAttributes();
    return string(atts.get(i).nname());
  }

  @Override
  public QName getAttributeName(final int i) {
    getAttributes();
    return atts.get(i).qname().toJava();
  }

  @Override
  public String getAttributeNamespace(final int i) {
    getAttributes();
    return string(atts.get(i).qname().uri().atom());
  }

  @Override
  public String getAttributePrefix(final int i) {
    getAttributes();
    return string(atts.get(i).qname().pref());
  }

  @Override
  public String getAttributeType(final int i) {
    getAttributes();
    final String name = getAttributeLocalName(i);
    for(final String a : ATTYPES) if(name.equals(a)) return name;
    return "CDATA";
  }

  /** Attribute types. */
  private static final String[] ATTYPES = {
    "ID", "IDREF", "IDREFS", "NMTOKEN", "NMTOKENS", "ENTITY", "ENTITIES"
  };

  @Override
  public String getAttributeValue(final int i) {
    getAttributes();
    return string(atts.get(i).atom());
  }

  @Override
  public String getAttributeValue(final String s, final String s1) {
    getAttributes();
    for(int a = 0; a < atts.size(); ++a) {
      if(!s1.equals(getAttributeLocalName(a))) continue;
      if(s == null || s.equals(getAttributeNamespace(a)))
        return getAttributeValue(a);
    }
    return null;
  }

  /** Retrieves the attributes for the current element. */
  private void getAttributes() {
    if(atts != null) return;
    checkType(START_ELEMENT, ATTRIBUTE);
    atts = new NodIter();
    final NodeIter iter = ((Nod) item).attr();
    try {
      while(true) {
        final Nod it = iter.next();
        if(it == null) return;
        atts.add(it);
      }
    } catch(final QueryException ex) {
      Util.stack(ex);
    }
  }

  @Override
  public String getCharacterEncodingScheme() {
    return null;
  }

  @Override
  public String getElementText() throws XMLStreamException {
    checkType(START_ELEMENT);
    next();

    final TokenBuilder tb = new TokenBuilder();
    while(kind != END_ELEMENT) {
      if(isType(CHARACTERS, CDATA, SPACE, ENTITY_REFERENCE)) {
        tb.add(item.atom());
      } else if(isType(END_DOCUMENT)) {
        throw new XMLStreamException("Unexpected end of document.");
      } else if(isType(START_ELEMENT)) {
        throw new XMLStreamException("START_ELEMENT not expected.");
      } else {
        checkType(PROCESSING_INSTRUCTION, COMMENT);
      }
      next();
    }
    return tb.toString();
  }

  @Override
  public String getEncoding() {
    return null;
  }

  @Override
  public int getEventType() {
    return kind;
  }

  @Override
  public String getLocalName() {
    checkType(START_ELEMENT, END_ELEMENT, ENTITY_REFERENCE);
    return string(((Nod) item).nname());
  }

  @Override
  public Location getLocation() {
    return new LocationImpl();
  }

  @Override
  public QName getName() {
    checkType(START_ELEMENT, END_ELEMENT, ENTITY_REFERENCE);
    return ((Nod) item).qname().toJava();
  }

  @Override
  public NamespaceContext getNamespaceContext() {
    return new BXNamespaceContext(ns);
  }

  @Override
  public int getNamespaceCount() {
    checkType(START_ELEMENT, END_ELEMENT, NAMESPACE);
    return 0;
  }

  @Override
  public String getNamespacePrefix(final int i) {
    checkType(START_ELEMENT, END_ELEMENT, NAMESPACE);
    return null;
  }

  @Override
  public String getNamespaceURI() {
    return null;
  }

  @Override
  public String getNamespaceURI(final String s) {
    if(s == null) throw new IllegalArgumentException();
    checkType(START_ELEMENT, END_ELEMENT, NAMESPACE);
    final byte[] uri = ns.find(token(s));
    return uri == null ? null : string(uri);
  }

  @Override
  public String getNamespaceURI(final int i) {
    checkType(START_ELEMENT, END_ELEMENT, NAMESPACE);
    return null;
  }

  @Override
  public String getPIData() {
    checkType(PROCESSING_INSTRUCTION);
    final byte[] val = item.atom();
    final int i = indexOf(val, ' ');
    return string(i == -1 ? EMPTY : substring(val, i + 1));
  }

  @Override
  public String getPITarget() {
    checkType(PROCESSING_INSTRUCTION);
    final byte[] val = item.atom();
    final int i = indexOf(val, ' ');
    return string(i == -1 ? val : substring(val, 0, i));
  }

  @Override
  public String getPrefix() {
    checkType(START_ELEMENT, END_ELEMENT);
    final QNm qn = ((Nod) item).qname();
    return !qn.ns() ? null : string(qn.pref());
  }

  @Override
  public Object getProperty(final String s) {
    if(s == null) throw new IllegalArgumentException();
    return PROPS.get(s);
  }

  @Override
  public String getText() {
    checkType(CHARACTERS, COMMENT);
    return string(item.atom());
  }

  @Override
  public char[] getTextCharacters() {
    return getText().toCharArray();
  }

  @Override
  public int getTextCharacters(final int ss, final char[] ac, final int ts,
      final int l) {

    checkType(CHARACTERS, COMMENT);
    final String value = getText();
    final int vl = value.length();
    if(ss >= vl) return 0;
    int se = ss + l;
    if(se > vl) se = value.length();
    value.getChars(ss, se, ac, ts);
    return se - ss;
  }

  @Override
  public int getTextLength() {
    checkType(CHARACTERS, COMMENT);
    return item.atom().length;
  }

  @Override
  public int getTextStart() {
    checkType(CHARACTERS, COMMENT);
    return 0;
  }

  @Override
  public String getVersion() {
    return "1.0";
  }

  @Override
  public boolean hasName() {
    return isType(START_ELEMENT, END_ELEMENT);
  }

  @Override
  public boolean hasNext() throws XMLStreamException {
    if(next) return true;
    next = true;
    atts = null;
    try {
      if(read != null) {
        if(read.hasNext()) {
          read.next();
        } else {
          read = null;
          kind = END_DOCUMENT;
          return true;
        }
      }
      if(read == null) {
        item = result.next();
        if(item instanceof DBNode) {
          read = new DNodeReader();
        } else if(item instanceof FNode) {
          read = new FNodeReader();
        } else if(item != null) {
          type();
        }
      }
    } catch(final QueryException ex) {
      throw new XMLStreamException(ex);
    }
    return item != null;
  }

  @Override
  public boolean hasText() {
    return isType(CHARACTERS, DTD, ENTITY_REFERENCE, COMMENT, SPACE);
  }

  @Override
  public boolean isAttributeSpecified(final int i) {
    checkType(START_ELEMENT, ATTRIBUTE);
    return true;
  }

  @Override
  public boolean isCharacters() {
    return isType(CHARACTERS);
  }

  @Override
  public boolean isEndElement() {
    return isType(END_ELEMENT);
  }

  @Override
  public boolean isStandalone() {
    return false;
  }

  @Override
  public boolean isStartElement() {
    return isType(START_ELEMENT);
  }

  @Override
  public boolean isWhiteSpace() {
    return isCharacters() && ws(item.atom());
  }

  @Override
  public int next() throws XMLStreamException {
    if(next && item == null || !next && !hasNext())
      throw new NoSuchElementException();

    next = false;
    // disallow top level attributes
    if(item.type == Type.ATT && read == null) throw new XMLStreamException();
    return kind;
  }

  @Override
  public int nextTag() throws XMLStreamException {
    next();
    while(kind == CHARACTERS && isWhiteSpace() ||
        kind == CDATA && isWhiteSpace() || kind == SPACE ||
        kind == PROCESSING_INSTRUCTION || kind == COMMENT) {
      next();
    }
    checkType(START_ELEMENT, END_ELEMENT);
    return kind;
  }

  @Override
  public void require(final int t, final String uri, final String ln)
      throws XMLStreamException {
    checkType(t);
    if(uri != null && !uri.equals(getNamespaceURI())) {
      throw new XMLStreamException();
    }
    if(ln != null && !ln.equals(getLocalName())) {
      throw new XMLStreamException();
    }
  }

  @Override
  public boolean standaloneSet() {
    return false;
  }

  /**
   * Sets the current event type.
   */
  void type() {
    switch(item.type) {
      case DOC: kind = START_DOCUMENT; return;
      case ATT: kind = ATTRIBUTE; return;
      case ELM: kind = START_ELEMENT; return;
      case COM: kind = COMMENT; return;
      case PI : kind = PROCESSING_INSTRUCTION; return;
      default:  kind = CHARACTERS; return;
    }
  }

  /**
   * Tests the validity of the specified types.
   * @param valid input types
   */
  private void checkType(final int... valid) {
    if(!isType(valid)) throw new IllegalStateException("Invalid Type: " + kind);
  }

  /**
   * Tests if one of the specified values matches the current kind.
   * @param valid input types
   * @return result of check
   */
  private boolean isType(final int... valid) {
    for(final int v : valid) if(kind == v) return true;
    return false;
  }

  /**
   * Reader for {@link FNode} instances.
   */
  abstract class NodeReader {
    /**
     * Checks if the node reader can return more nodes.
     * @return result of check
     */
    abstract boolean hasNext();
    /**
     * Checks if the node reader can return more nodes.
     */
    abstract void next();
  }

  /** Reader for traversing {@link DBNode} instances. */
  private final class DNodeReader extends NodeReader {
    /** Node reference. */
    private final DBNode node;
    /** Data size. */
    private final int s;
    /** Parent stack. */
    private final int[] parent = new int[IO.MAXHEIGHT];
    /** Pre stack. */
    private final int[] pre = new int[IO.MAXHEIGHT];
    /** Current level. */
    private int l;
    /** Current pre value. */
    private int p;

    /** Constructor. */
    DNodeReader() {
      node = ((DBNode) item).copy();
      item = node;
      p = node.pre;
      final int k = node.data.kind(p);
      s = p + node.data.size(p, k);
      finish(k, 0);
    }

    @Override
    boolean hasNext() {
      return p < s || l > 0;
    }

    @Override
    void next() {
      if(p == s) {
        endElem();
        return;
      }

      final Data data = node.data;
      final int k = data.kind(p);
      final int pa = data.parent(p, k);
      if(l > 0 && parent[l - 1] >= pa) {
        endElem();
        return;
      }
      finish(k, pa);
    }

    /**
     * Processes the end of an element.
     */
    private void endElem() {
      node.set(pre[--l], Data.ELEM);
      kind = END_ELEMENT;
    }

    /**
     * Finishing step.
     * @param k node kind
     * @param pa parent reference
     */
    private void finish(final int k, final int pa) {
      node.set(p, k);
      if(k == Data.ELEM) {
        pre[l] = p;
        parent[l++] = pa;
      }
      p += node.data.attSize(p, k);
      type();
    }
  }

  /** Reader for traversing {@link FNode} instances. */
  private final class FNodeReader extends NodeReader {
    /** Iterator. */
    private final NodeIter[] iter = new NodeIter[IO.MAXHEIGHT];
    /** Iterator. */
    private final Nod[] node = new Nod[IO.MAXHEIGHT];
    /** Iterator level. */
    private int l;

    /** Constructor. */
    FNodeReader() {
      iter[0] = ((FNode) item).self();
      hasNext();
    }

    @Override
    boolean hasNext() {
      try {
        final Nod n = iter[l].next();
        if(n != null) {
          node[l] = n;
          item = n;
          type();
          if(kind == START_ELEMENT) iter[++l] = n.child();
        } else {
          if(--l < 0) return false;
          item = node[l];
          kind = END_ELEMENT;
        }
      } catch(final QueryException ex) {
        Util.notexpected();
      }
      return true;
    }

    @Override
    void next() { }
  }

  /** Dummy location implementation. */
  static final class LocationImpl implements Location {
    @Override
    public int getCharacterOffset() { return -1; }
    @Override
    public int getColumnNumber() { return -1; }
    @Override
    public int getLineNumber() { return -1; }
    @Override
    public String getPublicId() { return null; }
    @Override
    public String getSystemId() { return null; }
  }
}
