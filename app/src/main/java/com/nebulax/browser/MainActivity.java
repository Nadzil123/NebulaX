package com.nebulax.browser;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {

    // ── Widgets ───────────────────────────────────────────────────
    private WebView             webView;
    private EditText            urlInput;
    private Button              btnBack, btnForward, btnReload, btnMore, btnClear;
    private Button              btnHome, btnBookmarks, btnHistory, btnShare, btnAddBookmark;
    private Button              findPrev, findNext, findClose;
    private EditText            findInput;
    private TextView            findCount, lockIcon;
    private ProgressBar         progressBar;
    private LinearLayout        findBar, toolbar, bottomBar;
    private FrameLayout         fullscreenContainer;
    private SwipeRefreshLayout  swipeRefresh;

    // ── State ─────────────────────────────────────────────────────
    private boolean isNightMode    = false;
    private boolean isDesktopMode  = false;
    private boolean isLoading      = false;
    private boolean isFullscreen   = false;
    private View    customView;
    private WebChromeClient.CustomViewCallback customViewCallback;

    private ValueCallback<Uri[]>  filePathCallback;
    private static final int      FILE_CHOOSER_REQUEST  = 1001;
    private static final int      DOWNLOAD_PERM_REQUEST = 1002;
    private String                pendingDownloadUrl;
    private String                pendingDownloadMime;
    private String                pendingDownloadName;

    private SharedPreferences prefs;
    private static final String PREFS_NAME    = "nebulax";
    private static final String KEY_BOOKMARKS = "bookmarks";
    private static final String KEY_HISTORY   = "history";
    private static final String KEY_NIGHT_MODE= "night_mode";
    private static final String KEY_HOME_URL  = "home_url";
    private static final String DEFAULT_HOME  = "https://www.google.com";
    private static final int    MAX_HISTORY   = 100;

    // ── Lifecycle ─────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        bindViews();
        setupWebView();
        setupUrlBar();
        setupNavButtons();
        setupFindBar();
        setupBottomBar();
        setupSwipeRefresh();

        if (prefs.getBoolean(KEY_NIGHT_MODE, false)) isNightMode = true;

        String intentUrl = getIntentUrl();
        webView.loadUrl(intentUrl != null ? intentUrl : prefs.getString(KEY_HOME_URL, DEFAULT_HOME));
    }

    @Override protected void onResume()  { super.onResume();  webView.onResume();  }
    @Override protected void onPause()   { super.onPause();   webView.onPause();   }
    @Override protected void onDestroy() { webView.destroy(); super.onDestroy();   }

    // ── View Binding ──────────────────────────────────────────────

    private void bindViews() {
        webView             = findViewById(R.id.webView);
        urlInput            = findViewById(R.id.urlInput);
        btnBack             = findViewById(R.id.btnBack);
        btnForward          = findViewById(R.id.btnForward);
        btnReload           = findViewById(R.id.btnReload);
        btnMore             = findViewById(R.id.btnMore);
        btnClear            = findViewById(R.id.btnClear);
        btnHome             = findViewById(R.id.btnHome);
        btnBookmarks        = findViewById(R.id.btnBookmarks);
        btnHistory          = findViewById(R.id.btnHistory);
        btnShare            = findViewById(R.id.btnShare);
        btnAddBookmark      = findViewById(R.id.btnAddBookmark);
        findBar             = findViewById(R.id.findBar);
        findInput           = findViewById(R.id.findInput);
        findPrev            = findViewById(R.id.findPrev);
        findNext            = findViewById(R.id.findNext);
        findClose           = findViewById(R.id.findClose);
        findCount           = findViewById(R.id.findCount);
        lockIcon            = findViewById(R.id.lockIcon);
        progressBar         = findViewById(R.id.progressBar);
        toolbar             = findViewById(R.id.toolbar);
        bottomBar           = findViewById(R.id.bottomBar);
        fullscreenContainer = findViewById(R.id.fullscreenContainer);
        swipeRefresh        = findViewById(R.id.swipeRefresh);
    }

    // ── WebView Setup ─────────────────────────────────────────────

    private void setupWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                isLoading = true;
                progressBar.setVisibility(View.VISIBLE);
                progressBar.setProgress(10);
                urlInput.setText(url);
                updateLockIcon(url);
                updateNavButtons();
                btnReload.setText("✕");
            }
            @Override
            public void onPageFinished(WebView view, String url) {
                isLoading = false;
                progressBar.setVisibility(View.GONE);
                urlInput.setText(url);
                updateLockIcon(url);
                updateNavButtons();
                btnReload.setText("⟳");
                swipeRefresh.setRefreshing(false);
                if (isNightMode) injectNightMode(true);
                addToHistory(url, view.getTitle() != null ? view.getTitle() : url);
            }
            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                showErrorPage(failingUrl, description);
            }
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (url.startsWith("tel:") || url.startsWith("mailto:") || url.startsWith("intent:")) {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
                    catch (Exception ignored) {}
                    return true;
                }
                return false;
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int progress) {
                progressBar.setProgress(progress);
                progressBar.setVisibility(progress == 100 ? View.GONE : View.VISIBLE);
            }
            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (customView != null) { callback.onCustomViewHidden(); return; }
                customView = view; customViewCallback = callback;
                fullscreenContainer.addView(view);
                fullscreenContainer.setVisibility(View.VISIBLE);
                toolbar.setVisibility(View.GONE);
                bottomBar.setVisibility(View.GONE);
                isFullscreen = true;
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            }
            @Override
            public void onHideCustomView() {
                if (customView == null) return;
                fullscreenContainer.removeView(customView);
                fullscreenContainer.setVisibility(View.GONE);
                customView = null;
                toolbar.setVisibility(View.VISIBLE);
                bottomBar.setVisibility(View.VISIBLE);
                isFullscreen = false;
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
                if (customViewCallback != null) customViewCallback.onCustomViewHidden();
            }
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> cb, FileChooserParams params) {
                if (filePathCallback != null) filePathCallback.onReceiveValue(null);
                filePathCallback = cb;
                try { startActivityForResult(params.createIntent(), FILE_CHOOSER_REQUEST); }
                catch (Exception e) { filePathCallback = null; return false; }
                return true;
            }
        });

        webView.setDownloadListener((url, ua, cd, mime, len) -> {
            pendingDownloadUrl = url; pendingDownloadMime = mime;
            pendingDownloadName = URLUtil.guessFileName(url, cd, mime);
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, DOWNLOAD_PERM_REQUEST);
                return;
            }
            startDownload(url, mime, pendingDownloadName);
        });
    }

    // ── URL Bar ───────────────────────────────────────────────────

    private void setupUrlBar() {
        urlInput.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            public void onTextChanged(CharSequence s, int st, int b, int c) {
                btnClear.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
            }
            public void afterTextChanged(Editable s) {}
        });
        urlInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO ||
               (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                navigate(urlInput.getText().toString()); return true;
            }
            return false;
        });
        btnClear.setOnClickListener(v -> { urlInput.setText(""); urlInput.requestFocus(); });
    }

    // ── Nav Buttons ───────────────────────────────────────────────

    private void setupNavButtons() {
        btnBack.setOnClickListener(v    -> { if (webView.canGoBack())    webView.goBack(); });
        btnForward.setOnClickListener(v -> { if (webView.canGoForward()) webView.goForward(); });
        btnReload.setOnClickListener(v -> {
            if (isLoading) { webView.stopLoading(); progressBar.setVisibility(View.GONE); isLoading=false; btnReload.setText("⟳"); }
            else webView.reload();
        });
        btnMore.setOnClickListener(v -> showMoreMenu());
        updateNavButtons();
    }

    private void updateNavButtons() {
        btnBack.setAlpha(webView.canGoBack() ? 1f : 0.35f);
        btnForward.setAlpha(webView.canGoForward() ? 1f : 0.35f);
    }

    private void updateLockIcon(String url) {
        boolean s = url != null && url.startsWith("https://");
        lockIcon.setText(s ? "🔒" : "⚠"); lockIcon.setAlpha(s ? 1f : 0.6f);
    }

    // ── Find in Page ──────────────────────────────────────────────

    private void setupFindBar() {
        findInput.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            public void onTextChanged(CharSequence s, int st, int b, int c) { webView.findAllAsync(s.toString()); }
            public void afterTextChanged(Editable s) {}
        });
        findInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) { webView.findNext(true); return true; }
            return false;
        });
        findPrev.setOnClickListener(v  -> webView.findNext(false));
        findNext.setOnClickListener(v  -> webView.findNext(true));
        findClose.setOnClickListener(v -> { findBar.setVisibility(View.GONE); webView.clearMatches(); hideKeyboard(); });
        webView.setFindListener((activeMatchOrdinal, numberOfMatches, isDone) -> {
            if (isDone) findCount.setText(numberOfMatches > 0 ? (activeMatchOrdinal+1)+"/"+numberOfMatches : "No results");
        });
    }

    private void showFindBar() {
        findBar.setVisibility(View.VISIBLE);
        findInput.requestFocus();
        ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(findInput, InputMethodManager.SHOW_IMPLICIT);
    }

    // ── Bottom Bar ────────────────────────────────────────────────

    private void setupBottomBar() {
        btnHome.setOnClickListener(v      -> webView.loadUrl(prefs.getString(KEY_HOME_URL, DEFAULT_HOME)));
        btnBookmarks.setOnClickListener(v -> showBookmarks());
        btnHistory.setOnClickListener(v   -> showHistory());
        btnShare.setOnClickListener(v     -> sharePage());
        btnAddBookmark.setOnClickListener(v -> {
            addBookmark();
            btnAddBookmark.setText("🌟");
            btnAddBookmark.postDelayed(() -> btnAddBookmark.setText("🔖"), 600);
        });
    }

    // ── Swipe to Refresh ──────────────────────────────────────────

    private void setupSwipeRefresh() {
        swipeRefresh.setColorSchemeColors(Color.parseColor("#5B8AF7"), Color.parseColor("#7C5BF7"));
        swipeRefresh.setProgressBackgroundColorSchemeColor(Color.parseColor("#1E2433"));
        swipeRefresh.setOnRefreshListener(() -> webView.reload());
    }

    // ── Navigation ────────────────────────────────────────────────

    private void navigate(String input) { webView.loadUrl(smartUrl(input.trim())); hideKeyboard(); }

    private String smartUrl(String input) {
        if (input.isEmpty()) return prefs.getString(KEY_HOME_URL, DEFAULT_HOME);
        if (input.startsWith("http://") || input.startsWith("https://")) return input;
        if (!input.contains(" ") && input.contains(".") && !input.startsWith(".")) return "https://" + input;
        return "https://www.google.com/search?q=" + Uri.encode(input);
    }

    // ── Night Mode ────────────────────────────────────────────────

    private void toggleNightMode() {
        isNightMode = !isNightMode;
        prefs.edit().putBoolean(KEY_NIGHT_MODE, isNightMode).apply();
        injectNightMode(isNightMode);
        toast(isNightMode ? "🌙 Night Mode ON" : "☀️ Night Mode OFF");
    }

    private void injectNightMode(boolean enable) {
        String js = enable
            ? "javascript:(function(){var id='nebula-night';if(document.getElementById(id))return;" +
              "var s=document.createElement('style');s.id=id;" +
              "s.textContent='html,body,*{background-color:#0d0f14!important;color:#e8ecf5!important;" +
              "border-color:#2a3045!important}img,video{filter:brightness(.85) saturate(.9)}" +
              "a{color:#5b8af7!important}';document.head.appendChild(s);})()"
            : "javascript:(function(){var s=document.getElementById('nebula-night');if(s)s.remove();})()";
        webView.loadUrl(js);
    }

    // ── Desktop Mode ──────────────────────────────────────────────

    private void toggleDesktopMode() {
        isDesktopMode = !isDesktopMode;
        webView.getSettings().setUserAgentString(isDesktopMode
            ? "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36" : null);
        webView.reload();
        toast(isDesktopMode ? "🖥️ Desktop Mode ON" : "📱 Mobile Mode");
    }

    // ── Bookmarks ─────────────────────────────────────────────────

    private void addBookmark() {
        String url = webView.getUrl(), title = webView.getTitle();
        if (url == null) return;
        if (title == null) title = url;
        try {
            JSONArray arr = getJsonArray(KEY_BOOKMARKS);
            for (int i=0;i<arr.length();i++) if (url.equals(arr.getJSONObject(i).optString("url"))) { toast("Already bookmarked ✓"); return; }
            JSONObject bm = new JSONObject(); bm.put("title", title); bm.put("url", url);
            arr.put(bm); saveJsonArray(KEY_BOOKMARKS, arr); toast("🔖 Bookmarked!");
        } catch (JSONException e) { toast("Error saving bookmark"); }
    }

    private void showBookmarks() {
        JSONArray arr = getJsonArray(KEY_BOOKMARKS);
        if (arr.length()==0) { toast("No bookmarks yet"); return; }
        String[] t=new String[arr.length()], u=new String[arr.length()];
        for (int i=0;i<arr.length();i++) { try { t[i]=arr.getJSONObject(i).optString("title","—"); u[i]=arr.getJSONObject(i).optString("url",""); } catch(JSONException e){t[i]="?";u[i]="";} }
        new AlertDialog.Builder(this).setTitle("⭐ Bookmarks")
            .setItems(t, (d,w)->webView.loadUrl(u[w]))
            .setNeutralButton("Manage", (d,w)->showManageBookmarks(arr)).show();
    }

    private void showManageBookmarks(JSONArray arr) {
        String[] t=new String[arr.length()];
        for (int i=0;i<arr.length();i++) { try{t[i]=arr.getJSONObject(i).optString("title","—");}catch(JSONException e){t[i]="?";} }
        new AlertDialog.Builder(this).setTitle("Delete Bookmark")
            .setItems(t, (d,w)->{ arr.remove(w); saveJsonArray(KEY_BOOKMARKS,arr); toast("Deleted"); }).show();
    }

    // ── History ───────────────────────────────────────────────────

    private void addToHistory(String url, String title) {
        try {
            JSONArray arr=getJsonArray(KEY_HISTORY); JSONObject e=new JSONObject();
            e.put("url",url); e.put("title",title); e.put("time",System.currentTimeMillis());
            JSONArray na=new JSONArray(); na.put(e);
            for (int i=0;i<arr.length()&&i<MAX_HISTORY-1;i++) na.put(arr.get(i));
            saveJsonArray(KEY_HISTORY, na);
        } catch (JSONException ignored) {}
    }

    private void showHistory() {
        JSONArray arr=getJsonArray(KEY_HISTORY);
        if (arr.length()==0) { toast("No history yet"); return; }
        SimpleDateFormat sdf=new SimpleDateFormat("dd/MM HH:mm", Locale.getDefault());
        String[] l=new String[arr.length()], u=new String[arr.length()];
        for (int i=0;i<arr.length();i++) {
            try { JSONObject o=arr.getJSONObject(i); long time=o.optLong("time",0);
                l[i]=(time>0?"["+sdf.format(new Date(time))+"] ":"")+o.optString("title","—"); u[i]=o.optString("url","");
            } catch(JSONException e){l[i]="?";u[i]="";}
        }
        new AlertDialog.Builder(this).setTitle("🕐 History")
            .setItems(l,(d,w)->webView.loadUrl(u[w]))
            .setNeutralButton("Clear All",(d,w)->{ saveJsonArray(KEY_HISTORY,new JSONArray()); toast("History cleared"); }).show();
    }

    // ── Share / Copy ──────────────────────────────────────────────

    private void sharePage() {
        String url=webView.getUrl(); if(url==null)return;
        Intent i=new Intent(Intent.ACTION_SEND); i.setType("text/plain");
        i.putExtra(Intent.EXTRA_TEXT,url); i.putExtra(Intent.EXTRA_SUBJECT,webView.getTitle());
        startActivity(Intent.createChooser(i,"Share via"));
    }

    private void copyUrl() {
        String url=webView.getUrl(); if(url==null)return;
        ((ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE))
            .setPrimaryClip(ClipData.newPlainText("URL",url));
        toast("URL copied 📋");
    }

    // ── Clear Data ────────────────────────────────────────────────

    private void clearData() {
        new AlertDialog.Builder(this).setTitle("Clear browsing data?")
            .setMessage("This will clear cache, cookies, and history.")
            .setPositiveButton("Clear",(d,w)->{
                webView.clearCache(true); webView.clearHistory();
                CookieManager.getInstance().removeAllCookies(null);
                saveJsonArray(KEY_HISTORY,new JSONArray()); toast("🗑️ Cleared");
            }).setNegativeButton("Cancel",null).show();
    }

    // ── Set Home ──────────────────────────────────────────────────

    private void setCurrentAsHome() {
        String url=webView.getUrl(); if(url==null)return;
        prefs.edit().putString(KEY_HOME_URL,url).apply(); toast("🏠 Home set to: "+url);
    }

    // ── Download ──────────────────────────────────────────────────

    private void startDownload(String url, String mimeType, String filename) {
        try {
            DownloadManager.Request req=new DownloadManager.Request(Uri.parse(url));
            req.setMimeType(mimeType); req.setTitle(filename);
            req.setDescription("Downloading via NebulaX");
            req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename);
            req.addRequestHeader("Cookie", CookieManager.getInstance().getCookie(url));
            req.addRequestHeader("User-Agent", webView.getSettings().getUserAgentString());
            ((DownloadManager)getSystemService(Context.DOWNLOAD_SERVICE)).enqueue(req);
            toast("⬇️ Downloading: "+filename);
        } catch (Exception e) { toast("Download failed: "+e.getMessage()); }
    }

    // ── Error Page ────────────────────────────────────────────────

    private void showErrorPage(String url, String desc) {
        webView.loadDataWithBaseURL(null,
            "<html><body style='background:#0d0f14;color:#e8ecf5;font-family:sans-serif;text-align:center;padding:60px 20px'>" +
            "<div style='font-size:64px'>🌐</div><h2 style='color:#f7615b;margin-top:16px'>Page Not Available</h2>" +
            "<p style='color:#7a839e;margin:12px 0'>"+desc+"</p>" +
            "<p style='font-size:12px;color:#2a3045;word-break:break-all'>"+url+"</p>" +
            "<button onclick='history.back()' style='margin-top:24px;padding:12px 28px;" +
            "background:#5b8af7;color:#fff;border:none;border-radius:12px;font-size:15px;cursor:pointer'>← Go Back</button>" +
            "</body></html>","text/html","UTF-8",null);
    }

    // ── More Menu ─────────────────────────────────────────────────

    private void showMoreMenu() {
        new AlertDialog.Builder(this).setTitle("NebulaX ✦").setItems(new String[]{
            "🔍  Find in page",
            "🌙  Night mode: "+(isNightMode?"ON ✓":"OFF"),
            "🖥️  Desktop mode: "+(isDesktopMode?"ON ✓":"OFF"),
            "📋  Copy URL","📤  Share","🔖  Bookmark this page",
            "⭐  View bookmarks","🕐  View history","🏠  Set as home page",
            "🔄  Hard reload","🗑️  Clear data"
        }, (d,w)->{
            switch(w){
                case 0:showFindBar();break; case 1:toggleNightMode();break;
                case 2:toggleDesktopMode();break; case 3:copyUrl();break;
                case 4:sharePage();break; case 5:addBookmark();break;
                case 6:showBookmarks();break; case 7:showHistory();break;
                case 8:setCurrentAsHome();break; case 9:hardReload();break;
                case 10:clearData();break;
            }
        }).show();
    }

    private void hardReload() {
        webView.getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);
        webView.reload();
        webView.getSettings().setCacheMode(WebSettings.LOAD_DEFAULT);
    }

    // ── Back Key ──────────────────────────────────────────────────

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (isFullscreen) { webView.getWebChromeClient().onHideCustomView(); return true; }
            if (findBar.getVisibility() == View.VISIBLE) { findBar.setVisibility(View.GONE); webView.clearMatches(); return true; }
            if (webView.canGoBack()) { webView.goBack(); return true; }
        }
        return super.onKeyDown(keyCode, event);
    }

    // ── Activity Results ──────────────────────────────────────────

    private String getIntentUrl() {
        Intent i=getIntent();
        return (i!=null && Intent.ACTION_VIEW.equals(i.getAction()) && i.getData()!=null) ? i.getData().toString() : null;
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        if (req==FILE_CHOOSER_REQUEST) {
            if (filePathCallback==null) return;
            Uri[] results=null;
            if (res==Activity.RESULT_OK && data!=null) {
                String ds=data.getDataString();
                if (ds!=null) { results=new Uri[]{Uri.parse(ds)}; }
                else if (data.getClipData()!=null) {
                    ClipData c=data.getClipData(); results=new Uri[c.getItemCount()];
                    for(int i=0;i<c.getItemCount();i++) results[i]=c.getItemAt(i).getUri();
                }
            }
            filePathCallback.onReceiveValue(results); filePathCallback=null;
        }
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] grants) {
        if (req==DOWNLOAD_PERM_REQUEST && grants.length>0 && grants[0]==PackageManager.PERMISSION_GRANTED && pendingDownloadUrl!=null)
            startDownload(pendingDownloadUrl, pendingDownloadMime, pendingDownloadName);
        else toast("Storage permission denied");
    }

    // ── Helpers ───────────────────────────────────────────────────

    private void toast(String msg) { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show(); }
    private void hideKeyboard() {
        InputMethodManager imm=(InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
        View f=getCurrentFocus(); if(f!=null) imm.hideSoftInputFromWindow(f.getWindowToken(),0);
    }
    private JSONArray getJsonArray(String key) {
        try { return new JSONArray(prefs.getString(key,"[]")); } catch(JSONException e) { return new JSONArray(); }
    }
    private void saveJsonArray(String key, JSONArray arr) { prefs.edit().putString(key,arr.toString()).apply(); }
}
