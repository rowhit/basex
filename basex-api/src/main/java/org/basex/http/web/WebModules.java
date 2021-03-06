package org.basex.http.web;

import static org.basex.http.web.WebText.*;
import static org.basex.util.Token.*;

import java.io.*;
import java.util.*;
import java.util.function.*;

import javax.servlet.http.*;

import org.basex.core.*;
import org.basex.http.*;
import org.basex.http.restxq.*;
import org.basex.http.ws.*;
import org.basex.io.*;
import org.basex.query.*;
import org.basex.query.ann.*;
import org.basex.query.value.item.*;
import org.basex.query.value.node.*;
import org.basex.util.*;
import org.basex.util.http.*;

/**
 * This class caches RESTXQ modules found in the HTTP root directory.
 *
 * @author BaseX Team 2005-18, BSD License
 * @author Christian Gruen
 */
public final class WebModules {
  /** Singleton instance. */
  private static volatile WebModules instance;

  /** RESTXQ path. */
  private final IOFile path;
  /** Indicates if modules should be parsed with every call. */
  private final boolean cached;

  /** Module cache. */
  private HashMap<String, WebModule> modules = new HashMap<>();
  /** Current parsing state. */
  private boolean parsed;
  /** Last access. */
  private long last;

  /**
   * Private constructor.
   * @param ctx database context
   */
  private WebModules(final Context ctx) {
    final StaticOptions sopts = ctx.soptions;
    final String webpath = sopts.get(StaticOptions.WEBPATH);
    final String rxqpath = sopts.get(StaticOptions.RESTXQPATH);
    path = new IOFile(webpath).resolve(rxqpath);

    // RESTXQ parsing
    final int ms = sopts.get(StaticOptions.PARSERESTXQ) * 1000;
    // = 0: parse every time
    cached = ms != 0;
    // >= 0: activate timer
    if(ms >= 0) {
      new Timer(true).scheduleAtFixedRate(new TimerTask() {
        @Override
        public void run() {
          if(System.currentTimeMillis() - last >= ms) init();
        }
      }, 0, 500);
    }
  }

  /**
   * Returns the singleton instance.
   * @param ctx database context
   * @return instance
   */
  public static WebModules get(final Context ctx) {
    if(instance == null) instance = new WebModules(ctx);
    return instance;
  }

  /**
   * Initializes the module cache.
   */
  public synchronized void init() {
    parsed = false;
  }

  /**
   * Returns a WADL description for all available URIs.
   * @param req HTTP request
   * @return WADL description
   */
  public FElem wadl(final HttpServletRequest req) {
    return new RestXqWadl(req).create(modules);
  }

  /**
   * Returns a RESTXQ function that matches the current request or the specified error code best.
   * @param conn HTTP connection
   * @param error error code (assigned if error function is to be called)
   * @return function, or {@code null} if no function matches
   * @throws QueryException query exception
   * @throws IOException I/O exception
   */
  public RestXqFunction restxq(final HTTPConnection conn, final QNm error)
      throws QueryException, IOException {

    // collect all function candidates
    List<RestXqFunction> funcs = find(conn, error, false);
    if(funcs.isEmpty()) return null;

    // remove functions with different specifity
    final RestXqFunction first = funcs.get(0);
    for(int l = funcs.size() - 1; l > 0; l--) {
      if(first.compareTo(funcs.get(l)) != 0) funcs.remove(l);
    }
    // return single function
    if(funcs.size() == 1) return first;

    // multiple functions: check quality factors
    funcs = bestQf(funcs, conn);
    if(funcs.size() == 1) return funcs.get(0);

    // show error if we are left with multiple function candidates
    throw first.path == null ?
      first.error(ERROR_CONFLICT_X_X, error, toString(funcs)) :
      first.error(PATH_CONFLICT_X_X, first.path, toString(funcs));
  }

  /**
   * Returns RESTXQ and permissions functions that match the current request.
   * @param conn HTTP connection
   * @param error error code (assigned if error function is to be called)
   * @param perm permission flag
   * @return list of matching functions, ordered by specifity
   * @throws QueryException query exception
   * @throws IOException I/O exception
   */
  private List<RestXqFunction> find(final HTTPConnection conn, final QNm error, final boolean perm)
      throws QueryException, IOException {

    // collect and sort all functions
    final ArrayList<RestXqFunction> list = new ArrayList<>();
    for(final WebModule mod : cache(conn.context).values()) {
      for(final RestXqFunction func : mod.functions()) {
        if(func.matches(conn, error, perm)) list.add(func);
      }
    }
    Collections.sort(list);
    return list;
  }

  /**
   * Returns permission functions that match the current request.
   * @param conn HTTP connection
   * @return list of function, ordered by relevance
   * @throws QueryException query exception
   * @throws IOException I/O exception
   */
  public List<RestXqFunction> checks(final HTTPConnection conn)
      throws QueryException, IOException {
    return find(conn, null, true);
  }

  /**
   * Returns all implementations for the given WebSocket.
   * @param ws WebSocket
   * @param ann annotation (can be {@code null})
   * @return result of check
   * @throws QueryException query exception
   * @throws IOException I/O exception
   */
  public ArrayList<WsFunction> findWs(final WebSocket ws, final Annotation ann)
      throws QueryException, IOException {
    final ArrayList<WsFunction> funcs = new ArrayList<>();
    for(final WebModule mod : cache(ws.context).values()) {
      for(final WsFunction func : mod.wsFunctions()) {
        if(func.matches(ann, ws.path)) funcs.add(func);
      }
    }
    Collections.sort(funcs);
    return funcs;
  }

  /**
   * Returns the WebSocket function that matches the current request.
   * @param ws WebSocket
   * @param ann annotation
   * @return function, or {@code null} if no function matches
   * @throws QueryException query exception
   * @throws IOException I/O exception
   */
  public WsFunction websocket(final WebSocket ws, final Annotation ann)
      throws QueryException, IOException {

    // collect and sort all function candidates
    final ArrayList<WsFunction> funcs = findWs(ws, ann);
    if(funcs.isEmpty()) return null;

    final WsFunction first = funcs.get(0);
    if(funcs.size() == 1) return first;

    // show error if we are left with multiple function candidates
    throw first.error(PATH_CONFLICT_X_X, first.path, toString(funcs));
  }

  /**
   * Returns a string representation of the specified functions.
   * @param funcs functions
   * @return string
   */
  private String toString(final List<? extends WebFunction> funcs) {
    final TokenBuilder tb = new TokenBuilder();
    for(final WebFunction func : funcs) {
      tb.add(Text.NL).add(Text.LI).addExt(func);
    }
    return tb.toString();
  }

  /**
   * Returns the functions with media type whose quality factors match best.
   * @param funcs list of functions
   * @param conn HTTP connection
   * @return list of matching functions
   */
  private static List<RestXqFunction> bestQf(final List<RestXqFunction> funcs,
      final HTTPConnection conn) {

    // find highest matching quality factors
    final MediaType[] accepts = conn.accepts();
    double cQf = 0, sQf = 0;
    for(final RestXqFunction func : funcs) {
      for(final MediaType accept : accepts) {
        if(func.produces.isEmpty()) {
          cQf = Math.max(cQf, qf(accept, "q"));
          sQf = 1;
        } else {
          for(final MediaType produce : func.produces) {
            if(produce.matches(accept)) {
              cQf = Math.max(cQf, qf(accept, "q"));
              sQf = Math.max(sQf, qf(produce, "qs"));
            }
          }
        }
      }
    }

    // find matching functions
    final List<RestXqFunction> list = bestQf(funcs, accepts, cQf, -1);
    return list.size() > 1 ? bestQf(funcs, accepts, cQf, sQf) : list;
  }

  /**
   * Returns the functions with media type whose quality factors match best.
   * @param funcs list of functions
   * @param accepts accept media types
   * @param clientQf client quality factor
   * @param serverQf server quality factor (ignore if {@code -1})
   * @return list of matching functions
   */
  private static List<RestXqFunction> bestQf(final List<RestXqFunction> funcs,
      final MediaType[] accepts, final double clientQf, final double serverQf) {

    final ArrayList<RestXqFunction> list = new ArrayList<>();
    for(final RestXqFunction func : funcs) {
      final BooleanSupplier s = () -> {
        for(final MediaType accept : accepts) {
          if(func.produces.isEmpty()) {
            if(qf(accept, "q") == clientQf) return true;
          } else {
            for(final MediaType produce : func.produces) {
              if(produce.matches(accept) && qf(accept, "q") == clientQf &&
                  (serverQf == -1 || qf(produce, "qs") == serverQf)) return true;
            }
          }
        }
        return false;
      };
      if(s.getAsBoolean()) list.add(func);
    }
    return list;
  }

  /**
   * Returns the quality factor of the specified media type.
   * @param mt media type
   * @param f quality factor string
   * @return quality factor
   */
  private static double qf(final MediaType mt, final String f) {
    final String qf = mt.parameters().get(f);
    return qf != null ? toDouble(token(qf)) : 1;
  }

  /**
   * Updates the module cache. Parses new modules and discards obsolete ones.
   * @param ctx database context
   * @return module cache
   * @throws QueryException query exception
   * @throws IOException I/O exception
   */
  private synchronized HashMap<String, WebModule> cache(final Context ctx)
      throws QueryException, IOException {

    if(!parsed) {
      if(!path.exists()) throw HTTPCode.NO_RESTXQ.get();

      final HashMap<String, WebModule> map = new HashMap<>();
      cache(ctx, path, map, modules);
      modules = map;
      parsed = cached;
    }
    last = System.currentTimeMillis();
    return modules;
  }

  /**
   * Parses the specified path for modules with relevant annotations and caches new entries.
   * @param root root path
   * @param ctx database context
   * @param cache cached modules
   * @param old old cache
   * @throws QueryException query exception
   * @throws IOException I/O exception
   */
  private static void cache(final Context ctx, final IOFile root,
      final HashMap<String, WebModule> cache, final HashMap<String, WebModule> old)
      throws QueryException, IOException {

    // check if directory is to be skipped
    final IOFile[] files = root.children();
    for(final IOFile file : files) {
      if(file.name().equals(IO.IGNORESUFFIX)) return;
    }

    for(final IOFile file : files) {
      if(file.isDir()) {
        cache(ctx, file, cache, old);
      } else {
        final String path = file.path();
        if(file.hasSuffix(IO.XQSUFFIXES)) {
          WebModule module = old.get(path);
          boolean parsed = false;
          if(module != null) {
            // check if module has been modified
            parsed = module.uptodate();
          } else {
            // create new module
            module = new WebModule(file);
          }
          // add module if it has been parsed, and if it contains relevant annotations
          if(parsed || module.parse(ctx)) {
            module.touch();
            cache.put(path, module);
          }
        }
      }
    }
  }
}
