package vn.dinhtrung.thuocla.web

/**
 * Builds the JavaScript the shell injects before any page script runs (task 3.2).
 *
 * Two jobs, both of which must happen before `app.js` executes:
 *
 * 1. **Seed** `localStorage` from Room. `app.js` reads `smoking_data`/`smoking_config`/`theme` in its
 *    own top-level `init()`, so seeding after `onPageFinished` would be too late — the UI would already
 *    have rendered empty. `WebViewCompat.addDocumentStartJavaScript` runs this in time. The seed is
 *    embedded in the script text itself, read from Room while the activity starts, so there is no
 *    bridge round-trip on the load path.
 * 2. **Write through** every mutation back to Room, so the store stays the source of truth and wiping
 *    WebView storage loses nothing.
 *
 * The write-through wraps the original `Storage.prototype` functions rather than replacing them, so
 * the page always sees its own write succeed. Native failures are logged and swallowed — a failed
 * persist must not break the UI (task 3.2); the next load re-seeds from Room, which is where a
 * discrepancy becomes visible instead of silent.
 *
 * `WebStore` is registered by [WebAppBridge]. If it is absent (a WebView too old for the document-start
 * hook), every call degrades to a no-op and the page runs exactly as it does in a browser.
 */
internal object WebStoreShim {

    /**
     * The seeding key names, and the four config fields, all taken from `app.js` v1.5.2.
     */
    private const val TEMPLATE = """
(function () {
  'use strict';
  if (window.__thuoclaShimInstalled) return;
  window.__thuoclaShimInstalled = true;

  var bridge = window.WebStore || null;
  var DB_KEY = 'smoking_data';
  var CFG_KEY = 'smoking_config';
  var THEME_KEY = 'theme';

  function safe(fn) {
    try { return fn(); } catch (e) { return null; }
  }

  // ---- 1. Seed from Room, before app.js reads anything ----
  var seed = __SEED__;
  safe(function () {
    window.localStorage.setItem(DB_KEY, JSON.stringify(seed.data || {}));
    window.localStorage.setItem(CFG_KEY, JSON.stringify(seed.config));
    window.localStorage.setItem(THEME_KEY, seed.theme);
  });

  // ---- 2. Forward every write to Room ----
  var proto = window.Storage && window.Storage.prototype;
  if (!proto) return;

  var originalSetItem = proto.setItem;
  var originalRemoveItem = proto.removeItem;

  // What Room is currently believed to hold, per day, so a whole-map rewrite is diffed instead of
  // re-sent, and so a day Room has that the new payload lacks can be cleared.
  //
  // Seeded from the same snapshot that was written above, which matters for THAY THẾ: `app.js`'s
  // restore writes the file's days over this map, so without the starting values a Room day absent
  // from the restored file would be unknown here and would survive the replacement.
  var forwarded = {};
  safe(function () {
    var initial = seed.data || {};
    Object.keys(initial).forEach(function (day) {
      forwarded[day] = JSON.stringify(initial[day]);
    });
  });

  function forward(key, value) {
    if (!bridge) return;
    try {
      if (key === DB_KEY && typeof value === 'string') {
        var data = JSON.parse(value);
        // Only the days whose content actually changed. The page rewrites the whole map on every
        // `saveData`, so without this a single +1 would re-send every day the user ever logged.
        Object.keys(data).forEach(function (day) {
          var encoded = JSON.stringify(data[day]);
          if (forwarded[day] !== encoded) {
            forwarded[day] = encoded;
            bridge.writeDay(day, encoded);
          }
        });
        // A day the page dropped entirely (a replaced restore) is cleared by writing it empty —
        // `writeDay` replaces a day, so an empty array means "no cigarettes left that day".
        Object.keys(forwarded).forEach(function (day) {
          if (!Object.prototype.hasOwnProperty.call(data, day)) {
            delete forwarded[day];
            bridge.writeDay(day, '[]');
          }
        });
      } else if (key === CFG_KEY && typeof value === 'string') {
        bridge.writeConfig(value);
      } else if (key === THEME_KEY && typeof value === 'string') {
        bridge.writeTheme(value);
      }
    } catch (e) {
      // Never surface a native failure into the page: the write already succeeded in localStorage.
      console.warn('thuocla: write-through failed for ' + key, e);
    }
  }

  proto.setItem = function (key, value) {
    var result = originalSetItem.call(this, key, value);
    forward(key, value);
    return result;
  };

  proto.removeItem = function (key) {
    var result = originalRemoveItem.call(this, key);
    // `clearAllData()` removes the log; `clear()` is the same intent from the shell.
    if (key === DB_KEY && bridge) {
      try {
        bridge.clearRecords();
        // The cache must go with it. `clearAllData()` removes only the key — the page's own in-memory
        // records survive — so a cigarette re-added at a minute that was already recorded arrives as a
        // payload identical to the one the cache holds, gets suppressed as "unchanged", and the page
        // shows a cigarette Room does not have. Resetting makes the next write unconditional.
        forwarded = {};
      } catch (e) {
        console.warn('thuocla: clear failed', e);
      }
    }
    return result;
  };
})();
"""

    /**
     * The script with [seedJson] (from `WebStoreMirror.readSeed`) inlined as a JS value.
     *
     * `U+2028`/`U+2029` are escaped because they terminate a line in the JavaScript grammar even
     * inside a string literal, so an unescaped one in a note would make the whole script a syntax
     * error — and the page would silently lose the seed.
     */
    fun script(seedJson: String): String = TEMPLATE.replace("__SEED__", escapeForJs(seedJson))

    private fun escapeForJs(json: String): String =
        json.replace("\u2028", "\\u2028").replace("\u2029", "\\u2029")
}
