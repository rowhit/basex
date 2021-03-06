package org.basex.query.expr.path;

import org.basex.query.iter.*;
import org.basex.query.value.node.*;
import org.basex.util.*;

/**
 * XPath axes.
 *
 * @author BaseX Team 2005-18, BSD License
 * @author Christian Gruen
 */
public enum Axis {
  // ...order is important here for parsing the Query;
  // axes with longer names are parsed first

  /** Ancestor-or-self axis. */
  ANCESTOR_OR_SELF("ancestor-or-self", false) {
    @Override
    BasicNodeIter iter(final ANode n) {
      return n.ancestorOrSelf();
    }
  },

  /** Ancestor axis. */
  ANCESTOR("ancestor", false) {
    @Override
    BasicNodeIter iter(final ANode n) {
      return n.ancestor();
    }
  },

  /** Attribute axis. */
  ATTRIBUTE("attribute", true) {
    @Override
    BasicNodeIter iter(final ANode n) {
      return n.attributes();
    }
  },

  /** Child Axis. */
  CHILD("child", true) {
    @Override
    BasicNodeIter iter(final ANode n) {
      return n.children();
    }
  },

  /** Descendant-or-self axis. */
  DESCENDANT_OR_SELF("descendant-or-self", true) {
    @Override
    BasicNodeIter iter(final ANode n) {
      return n.descendantOrSelf();
    }
  },

  /** Descendant axis. */
  DESCENDANT("descendant", true) {
    @Override
    BasicNodeIter iter(final ANode n) {
      return n.descendant();
    }
  },

  /** Following-Sibling axis. */
  FOLLOWING_SIBLING("following-sibling", false) {
    @Override
    BasicNodeIter iter(final ANode n) {
      return n.followingSibling();
    }
  },

  /** Following axis. */
  FOLLOWING("following", false) {
    @Override
    BasicNodeIter iter(final ANode n) {
      return n.following();
    }
  },

  /** Parent axis. */
  PARENT("parent", false) {
    @Override
    BasicNodeIter iter(final ANode n) {
      return n.parentIter();
    }
  },

  /** Preceding-Sibling axis. */
  PRECEDING_SIBLING("preceding-sibling", false) {
    @Override
    BasicNodeIter iter(final ANode n) {
      return n.precedingSibling();
    }
  },

  /** Preceding axis. */
  PRECEDING("preceding", false) {
    @Override
    BasicNodeIter iter(final ANode n) {
      return n.preceding();
    }
  },

  /** Step axis. */
  SELF("self", true) {
    @Override
    BasicNodeIter iter(final ANode n) {
      return n.self();
    }
  };

  /** Cached enums (faster). */
  public static final Axis[] VALUES = values();
  /** Axis string. */
  public final String name;
  /** Descendant axis flag. */
  public final boolean down;

  /**
   * Constructor.
   * @param name axis string
   * @param down descendant flag
   */
  Axis(final String name, final boolean down) {
    this.name = name;
    this.down = down;
  }

  /**
   * Returns a node iterator.
   * @param n input node
   * @return node iterator
   */
  abstract BasicNodeIter iter(ANode n);

  /**
   * Checks if this is one of the specified axes.
   * @param axes axes
   * @return result of check
   */
  boolean oneOf(final Axis... axes) {
    for(final Axis axis : axes) {
      if(this == axis) return true;
    }
    return false;
  }

  @Override
  public String toString() {
    return name;
  }

  /**
   * Inverts the axis.
   * @return inverted axis
   */
  final Axis invert() {
    switch(this) {
      case ANCESTOR:           return DESCENDANT;
      case ANCESTOR_OR_SELF:   return DESCENDANT_OR_SELF;
      case ATTRIBUTE:
      case CHILD:              return PARENT;
      case DESCENDANT:         return ANCESTOR;
      case DESCENDANT_OR_SELF: return ANCESTOR_OR_SELF;
      case FOLLOWING_SIBLING:  return PRECEDING_SIBLING;
      case FOLLOWING:          return PRECEDING;
      case PARENT:             return CHILD;
      case PRECEDING_SIBLING:  return FOLLOWING_SIBLING;
      case PRECEDING:          return FOLLOWING;
      case SELF:               return SELF;
      default:                 throw Util.notExpected();
    }
  }
}
