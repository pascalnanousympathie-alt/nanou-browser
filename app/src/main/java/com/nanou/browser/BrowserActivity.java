package com.nanou.browser;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.SslErrorHandler;
import android.webkit.URLUtil;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class BrowserActivity extends Activity {
    private static final int STORAGE_PERMISSION_REQUEST = 413;
    private static final int MAX_TABS = 12;
    private static final int MAX_HISTORY = 120;
    private static final String PREFS = "nanou_browser_local";
    private static final String KEY_BOOKMARKS = "bookmarks";
    private static final String KEY_HISTORY = "history";
    private static final int NAVY = Color.rgb(17, 24, 39);
    private static final int TEAL = Color.rgb(8, 127, 140);
    private static final int GOLD = Color.rgb(230, 184, 77);
    private static final int SURFACE = Color.rgb(244, 246, 250);
    private static final int TEXT = Color.rgb(31, 41, 55);
    private static final int MUTED = Color.rgb(107, 114, 128);

    private final List<BrowserTab> tabs = new ArrayList<>();
    private LinearLayout root;
    private LinearLayout tabsRow;
    private FrameLayout pageHost;
    private EditText addressBox;
    private Button backButton;
    private Button forwardButton;
    private Button bookmarkButton;
    private TextView statusText;
    private ProgressBar progressBar;
    private BrowserTab activeTab;
    private int nextTabId = 1;
    private String pendingDownloadUrl;
    private String pendingDownloadAgent;
    private String pendingDownloadDisposition;
    private String pendingDownloadMime;

    private static final class BrowserTab {
        int id;
        WebView webView;
        String url = BrowserUrlResolver.HOME_URL;
        String lastCommittedUrl = BrowserUrlResolver.HOME_URL;
        String title = "Nanou Browser";
        boolean loading;
        boolean mainFrameFailed;
    }

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(NAVY);
        getWindow().setNavigationBarColor(NAVY);
        WebView.setWebContentsDebuggingEnabled(false);
        buildUi();
        addTab(true);
    }

    private void buildUi() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(SURFACE);
        root.setFitsSystemWindows(true);

        LinearLayout brandRow = new LinearLayout(this);
        brandRow.setGravity(Gravity.CENTER_VERTICAL);
        brandRow.setPadding(dp(12), dp(5), dp(8), dp(4));
        brandRow.setBackgroundColor(NAVY);

        TextView logo = new TextView(this);
        logo.setText("N");
        logo.setTextColor(NAVY);
        logo.setTextSize(18);
        logo.setTypeface(Typeface.DEFAULT_BOLD);
        logo.setGravity(Gravity.CENTER);
        logo.setBackground(rounded(GOLD, 12));
        brandRow.addView(logo, new LinearLayout.LayoutParams(dp(34), dp(34)));

        TextView title = new TextView(this);
        title.setText("Nanou Browser");
        title.setTextColor(Color.WHITE);
        title.setTextSize(17);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(dp(9), 0, dp(4), 0);
        brandRow.addView(title, new LinearLayout.LayoutParams(0, dp(40), 1));

        bookmarkButton = chromeButton("☆", false);
        bookmarkButton.setContentDescription("Tehirizo ny tsoratadidy");
        bookmarkButton.setOnClickListener(v -> addBookmark());
        brandRow.addView(bookmarkButton, new LinearLayout.LayoutParams(dp(40), dp(38)));

        Button newTab = chromeButton("+", true);
        newTab.setContentDescription("Onglet vaovao");
        newTab.setOnClickListener(v -> addTab(true));
        brandRow.addView(newTab, new LinearLayout.LayoutParams(dp(40), dp(38)));

        Button more = chromeButton("⋮", false);
        more.setContentDescription("Safidy fanampiny");
        more.setOnClickListener(this::showMenu);
        brandRow.addView(more, new LinearLayout.LayoutParams(dp(40), dp(38)));
        root.addView(brandRow);

        HorizontalScrollView tabsScroll = new HorizontalScrollView(this);
        tabsScroll.setHorizontalScrollBarEnabled(false);
        tabsScroll.setBackgroundColor(NAVY);
        tabsRow = new LinearLayout(this);
        tabsRow.setOrientation(LinearLayout.HORIZONTAL);
        tabsRow.setPadding(dp(8), 0, dp(8), dp(7));
        tabsScroll.addView(tabsRow);
        root.addView(tabsScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(42)));

        LinearLayout navRow = new LinearLayout(this);
        navRow.setGravity(Gravity.CENTER_VERTICAL);
        navRow.setPadding(dp(5), dp(5), dp(5), dp(5));
        navRow.setBackgroundColor(Color.WHITE);

        backButton = chromeButton("‹", false);
        backButton.setContentDescription("Miverina");
        backButton.setOnClickListener(v -> goBack());
        navRow.addView(backButton, new LinearLayout.LayoutParams(dp(35), dp(40)));

        forwardButton = chromeButton("›", false);
        forwardButton.setContentDescription("Mandroso");
        forwardButton.setOnClickListener(v -> goForward());
        navRow.addView(forwardButton, new LinearLayout.LayoutParams(dp(35), dp(40)));

        Button reloadButton = chromeButton("⟳", false);
        reloadButton.setContentDescription("Avereno");
        reloadButton.setOnClickListener(v -> reload());
        navRow.addView(reloadButton, new LinearLayout.LayoutParams(dp(38), dp(40)));

        addressBox = new EditText(this);
        addressBox.setSingleLine(true);
        addressBox.setTextSize(14);
        addressBox.setTextColor(TEXT);
        addressBox.setHintTextColor(MUTED);
        addressBox.setHint("Karohy na ampidiro URL");
        addressBox.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        addressBox.setImeOptions(EditorInfo.IME_ACTION_GO);
        addressBox.setSelectAllOnFocus(true);
        addressBox.setPadding(dp(10), 0, dp(8), 0);
        addressBox.setBackground(rounded(Color.rgb(238, 241, 246), 12));
        addressBox.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN)) {
                navigateFromAddress();
                return true;
            }
            return false;
        });
        navRow.addView(addressBox, new LinearLayout.LayoutParams(0, dp(40), 1));

        Button go = chromeButton("Go", true);
        go.setContentDescription("Mandehana");
        go.setOnClickListener(v -> navigateFromAddress());
        LinearLayout.LayoutParams goParams = new LinearLayout.LayoutParams(dp(46), dp(40));
        goParams.leftMargin = dp(4);
        navRow.addView(go, goParams);
        root.addView(navRow);

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setProgress(0);
        progressBar.setProgressTintList(android.content.res.ColorStateList.valueOf(TEAL));
        progressBar.setVisibility(View.GONE);
        root.addView(progressBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(2)));

        pageHost = new FrameLayout(this);
        pageHost.setBackgroundColor(Color.WHITE);
        root.addView(pageHost, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        LinearLayout bottom = new LinearLayout(this);
        bottom.setGravity(Gravity.CENTER);
        bottom.setPadding(dp(5), dp(4), dp(5), dp(4));
        bottom.setBackgroundColor(Color.WHITE);
        bottom.setElevation(dp(4));

        addBottomButton(bottom, "⌂", "Home", v -> openHome());
        addBottomButton(bottom, "★", "Tsoratadidy", v -> showBookmarks());
        addBottomButton(bottom, "◷", "Tantara", v -> showHistory());
        addBottomButton(bottom, "↗", "Zarao", v -> shareCurrentPage());
        root.addView(bottom, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));

        statusText = new TextView(this);
        statusText.setText("Vonona");
        statusText.setTextColor(MUTED);
        statusText.setTextSize(10);
        statusText.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        statusText.setPadding(dp(12), 0, dp(12), 0);
        statusText.setBackgroundColor(SURFACE);
        root.addView(statusText, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(20)));

        setContentView(root);
    }

    private void addBottomButton(LinearLayout row, String glyph, String label, View.OnClickListener click) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        Button button = chromeButton(glyph, false);
        button.setTextSize(19);
        button.setBackgroundColor(Color.TRANSPARENT);
        button.setOnClickListener(click);
        item.addView(button, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(27)));
        TextView caption = new TextView(this);
        caption.setText(label);
        caption.setTextSize(10);
        caption.setTextColor(TEXT);
        caption.setGravity(Gravity.CENTER);
        item.addView(caption, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(16)));
        row.addView(item, new LinearLayout.LayoutParams(0, dp(44), 1));
    }

    private Button chromeButton(String label, boolean primary) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(16);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setPadding(dp(3), 0, dp(3), 0);
        b.setAllCaps(false);
        b.setTextColor(primary ? NAVY : TEXT);
        b.setBackground(rounded(primary ? GOLD : Color.rgb(238, 241, 246), 10));
        return b;
    }

    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        return d;
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void addTab(boolean loadHome) {
        if (tabs.size() >= MAX_TABS) {
            toast("Feno ny isan'ny onglet (12). Akatona ny iray aloha.");
            return;
        }
        BrowserTab tab = new BrowserTab();
        tab.id = nextTabId++;
        tab.webView = createWebView(tab);
        pageHost.addView(tab.webView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        tab.webView.setVisibility(View.GONE);
        tabs.add(tab);
        selectTab(tab);
        if (loadHome) loadHome(tab);
    }

    private WebView createWebView(BrowserTab tab) {
        WebView web = new WebView(this);
        web.setBackgroundColor(Color.WHITE);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(false);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
            CookieManager.getInstance().setAcceptThirdPartyCookies(web, false);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) web.setSafeBrowsingEnabled(true);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return routeNavigation(tab, request.getUrl().toString());
            }
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return routeNavigation(tab, url);
            }
            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                tab.loading = true;
                tab.mainFrameFailed = false;
                if (BrowserUrlResolver.isHomeUrl(url)) {
                    tab.url = BrowserUrlResolver.HOME_URL;
                    tab.title = "Nanou Browser";
                } else if (BrowserUrlResolver.isWebUrl(url)) {
                    tab.url = url;
                }
                if (tab == activeTab) {
                    updateChrome();
                    progressBar.setVisibility(View.VISIBLE);
                    progressBar.setProgress(5);
                    statusText.setText("Mampiditra pejy…");
                }
                refreshTabsRow();
            }
            @Override
            public void onPageFinished(WebView view, String url) {
                tab.loading = false;
                if (BrowserUrlResolver.isHomeUrl(url)) {
                    tab.url = BrowserUrlResolver.HOME_URL;
                    tab.title = "Nanou Browser";
                    if (!tab.mainFrameFailed) tab.lastCommittedUrl = BrowserUrlResolver.HOME_URL;
                } else if (BrowserUrlResolver.isWebUrl(url)) {
                    tab.url = url;
                    if (!tab.mainFrameFailed) tab.lastCommittedUrl = url;
                    String pageTitle = view.getTitle();
                    tab.title = pageTitle != null && !pageTitle.trim().isEmpty()
                            ? pageTitle.trim() : hostLabel(url);
                    addHistory(tab.title, url);
                }
                if (tab == activeTab) {
                    progressBar.setVisibility(View.GONE);
                    statusText.setText("Vita · " + tab.title);
                    updateChrome();
                }
                refreshTabsRow();
            }
            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) {
                    tab.mainFrameFailed = true;
                    if (tab == activeTab) {
                        progressBar.setVisibility(View.GONE);
                        statusText.setText("Tsy tafiditra ny pejy. Jereo ny tambajotra na ny adiresy.");
                    }
                }
            }
            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.cancel();
                if (tab == activeTab) statusText.setText("Nofoanana: tsy azo itokisana ny certificat SSL.");
            }
            @Override
            public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                handleRendererCrash(tab, view);
                return true;
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (tab == activeTab) {
                    progressBar.setProgress(newProgress);
                    progressBar.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE);
                }
            }
            @Override
            public void onReceivedTitle(WebView view, String title) {
                if (title != null && !title.trim().isEmpty()) {
                    tab.title = title.trim();
                    if (tab == activeTab) refreshTabsRow();
                }
            }
        });
        web.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) ->
                requestDownload(url, userAgent, contentDisposition, mimeType));
        return web;
    }

    private boolean routeNavigation(BrowserTab tab, String value) {
        if (value == null || value.isEmpty()) return true;
        if (BrowserUrlResolver.isHomeUrl(value)) {
            loadHome(tab);
            return true;
        }
        if (BrowserUrlResolver.isWebUrl(value)) return false;
        Uri uri = Uri.parse(value);
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if ("mailto".equals(scheme) || "tel".equals(scheme)) {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, uri));
            } catch (ActivityNotFoundException ignored) {
                toast("Tsy misy application afaka manokatra ity rohy ity.");
            }
            return true;
        }
        toast("Nosakanana ny karazana rohy tsy ekena.");
        return true;
    }

    private void handleRendererCrash(BrowserTab tab, WebView crashedView) {
        boolean wasActive = tab == activeTab;
        if (crashedView.getParent() instanceof ViewGroup) {
            ((ViewGroup) crashedView.getParent()).removeView(crashedView);
        }
        try {
            crashedView.stopLoading();
            crashedView.destroy();
        } catch (Throwable ignored) {}
        tab.webView = createWebView(tab);
        pageHost.addView(tab.webView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        tab.webView.setVisibility(wasActive ? View.VISIBLE : View.GONE);

        // Restore the last committed address for background tabs too. Otherwise a
        // renderer crash replaces the WebView with an empty one that stays blank
        // when the user switches back to that tab.
        if (BrowserUrlResolver.isHomeUrl(tab.lastCommittedUrl)
                || !BrowserUrlResolver.isWebUrl(tab.lastCommittedUrl)) {
            loadHome(tab);
        } else {
            loadUrl(tab, tab.lastCommittedUrl);
        }
        if (wasActive) toast("Niverina ny pejy rehefa nijanona ny renderer.");
    }

    private void selectTab(BrowserTab tab) {
        activeTab = tab;
        for (BrowserTab candidate : tabs) {
            candidate.webView.setVisibility(candidate == tab ? View.VISIBLE : View.GONE);
        }
        updateChrome();
        refreshTabsRow();
    }

    private void refreshTabsRow() {
        if (tabsRow == null) return;
        tabsRow.removeAllViews();
        for (BrowserTab tab : tabs) {
            LinearLayout chip = new LinearLayout(this);
            chip.setGravity(Gravity.CENTER_VERTICAL);
            chip.setPadding(dp(4), 0, dp(2), 0);
            chip.setBackground(rounded(tab == activeTab ? TEAL : Color.rgb(42, 52, 68), 9));
            TextView label = new TextView(this);
            label.setText(tab.title == null || tab.title.trim().isEmpty() ? "Onglet" : truncate(tab.title, 16));
            label.setTextColor(Color.WHITE);
            label.setTextSize(12);
            label.setGravity(Gravity.CENTER_VERTICAL);
            label.setPadding(dp(7), 0, dp(3), 0);
            label.setMaxWidth(dp(130));
            label.setSingleLine(true);
            label.setOnClickListener(v -> selectTab(tab));
            chip.addView(label, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(31)));
            TextView close = new TextView(this);
            close.setText("×");
            close.setTextSize(17);
            close.setTextColor(Color.WHITE);
            close.setGravity(Gravity.CENTER);
            close.setContentDescription("Akatona onglet");
            close.setOnClickListener(v -> closeTab(tab));
            chip.addView(close, new LinearLayout.LayoutParams(dp(27), dp(31)));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(33));
            params.rightMargin = dp(6);
            tabsRow.addView(chip, params);
        }
    }

    private String truncate(String text, int max) {
        if (text == null) return "";
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }

    private void updateChrome() {
        if (activeTab == null || addressBox == null) return;
        String displayed = BrowserUrlResolver.HOME_URL.equals(activeTab.url) ? "" : activeTab.url;
        if (!displayed.equals(addressBox.getText().toString())) {
            addressBox.setText(displayed);
            addressBox.setSelection(addressBox.getText().length());
        }
        boolean canBack = activeTab.webView != null && activeTab.webView.canGoBack();
        boolean canForward = activeTab.webView != null && activeTab.webView.canGoForward();
        backButton.setAlpha(canBack ? 1f : 0.42f);
        forwardButton.setAlpha(canForward ? 1f : 0.42f);
        bookmarkButton.setText(isBookmarked(activeTab.url) ? "★" : "☆");
    }

    private void navigateFromAddress() {
        if (activeTab == null) return;
        String target = BrowserUrlResolver.resolve(addressBox.getText().toString());
        ((android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
                .hideSoftInputFromWindow(addressBox.getWindowToken(), 0);
        addressBox.clearFocus();
        if (BrowserUrlResolver.HOME_URL.equals(target)) loadHome(activeTab);
        else loadUrl(activeTab, target);
    }

    private void loadUrl(BrowserTab tab, String url) {
        if (!BrowserUrlResolver.isWebUrl(url)) {
            toast("Adiresy tsy ekena.");
            return;
        }
        tab.url = url;
        tab.title = hostLabel(url);
        tab.webView.loadUrl(url);
        if (tab == activeTab) statusText.setText("Mampiditra " + hostLabel(url) + "…");
        updateChrome();
        refreshTabsRow();
    }

    private void loadHome(BrowserTab tab) {
        tab.url = BrowserUrlResolver.HOME_URL;
        tab.lastCommittedUrl = BrowserUrlResolver.HOME_URL;
        tab.mainFrameFailed = false;
        tab.title = "Nanou Browser";
        tab.loading = false;
        tab.webView.loadDataWithBaseURL("https://nanou.browser/", homeHtml(),
                "text/html", "UTF-8", null);
        if (tab == activeTab) {
            progressBar.setVisibility(View.GONE);
            statusText.setText("Vonona · DuckDuckGo no motera fikarohana");
            updateChrome();
        }
        refreshTabsRow();
    }

    private String homeHtml() {
        return "<!doctype html><html lang='mg'><head><meta charset='utf-8'/>"
                + "<meta name='viewport' content='width=device-width,initial-scale=1'/>"
                + "<meta name='color-scheme' content='light'/><title>Nanou Browser</title><style>"
                + "*{box-sizing:border-box}body{margin:0;font-family:Arial,sans-serif;background:#f4f6fa;color:#111827}"
                + ".wrap{max-width:640px;margin:0 auto;padding:11vh 20px 40px}"
                + ".brand{display:flex;align-items:center;justify-content:center;gap:12px;margin-bottom:18px}"
                + ".mark{width:52px;height:52px;border-radius:16px;background:#e6b84d;display:grid;place-items:center;font-size:28px;font-weight:800}"
                + "h1{font-size:29px;margin:0;letter-spacing:-.7px}p{color:#6b7280;line-height:1.5}"
                + ".search{display:flex;gap:8px;margin:24px 0 16px}"
                + "input{flex:1;min-width:0;padding:15px;border:1px solid #d7dce5;border-radius:13px;font-size:16px;background:#fff;color:#111827}"
                + "button{padding:0 17px;border:0;border-radius:13px;background:#087f8c;color:#fff;font-weight:700;font-size:15px}"
                + ".links{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:10px;margin-top:23px}"
                + ".link{padding:14px;background:#fff;border:1px solid #e0e5ec;border-radius:13px;text-decoration:none;color:#111827;font-weight:600}"
                + ".link small{display:block;font-weight:400;color:#6b7280;margin-top:5px}.note{font-size:12px;margin-top:28px;text-align:center}"
                + "</style></head><body><main class='wrap'><div class='brand'><div class='mark'>N</div>"
                + "<div><h1>Nanou Browser</h1><div style='color:#087f8c;font-size:13px;font-weight:700'>Mandehana amin'ny Internet</div></div></div>"
                + "<p>Mitadiava vaovao, sokafy tranonkala, ary tantano ireo onglet-nao amin'ny toerana iray.</p>"
                + "<form class='search' action='https://duckduckgo.com/' method='get'>"
                + "<input name='q' aria-label='Fikarohana' placeholder='Inona no tadiavinao?' autocomplete='off'/>"
                + "<button type='submit'>Karohy</button></form>"
                + "<div class='links'>"
                + "<a class='link' href='https://duckduckgo.com/'>DuckDuckGo<small>Fikarohana</small></a>"
                + "<a class='link' href='https://www.wikipedia.org/'>Wikipedia<small>Fahalalana</small></a>"
                + "<a class='link' href='https://www.youtube.com/'>YouTube<small>Lahatsary</small></a>"
                + "<a class='link' href='https://github.com/'>GitHub<small>Kaody sy projet</small></a>"
                + "</div><p class='note'>Tsy mandefa tantara na bookmark any amin'ny serveur i Nanou Browser. Tehirizina eto amin'ny fitaovanao izany.</p>"
                + "</main></body></html>";
    }

    private void openHome() {
        if (activeTab == null) addTab(true);
        else loadHome(activeTab);
    }

    private void goBack() {
        if (activeTab != null && activeTab.webView.canGoBack()) activeTab.webView.goBack();
        else openHome();
    }

    private void goForward() {
        if (activeTab != null && activeTab.webView.canGoForward()) activeTab.webView.goForward();
    }

    private void reload() {
        if (activeTab == null) return;
        if (BrowserUrlResolver.isHomeUrl(activeTab.url)) loadHome(activeTab);
        else activeTab.webView.reload();
    }

    private void closeTab(BrowserTab tab) {
        int index = tabs.indexOf(tab);
        if (index < 0) return;
        boolean wasActive = activeTab == tab;
        tabs.remove(tab);
        try {
            if (tab.webView.getParent() instanceof ViewGroup) {
                ((ViewGroup) tab.webView.getParent()).removeView(tab.webView);
            }
            tab.webView.stopLoading();
            tab.webView.destroy();
        } catch (Throwable ignored) {}
        if (tabs.isEmpty()) {
            activeTab = null;
            addTab(true);
        } else if (wasActive) {
            selectTab(tabs.get(Math.max(0, Math.min(index - 1, tabs.size() - 1))));
        } else {
            refreshTabsRow();
        }
    }

    private void showMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add("Onglet vaovao").setOnMenuItemClickListener(item -> { addTab(true); return true; });
        menu.getMenu().add("Tsoratadidy").setOnMenuItemClickListener(item -> { showBookmarks(); return true; });
        menu.getMenu().add("Tantara").setOnMenuItemClickListener(item -> { showHistory(); return true; });
        menu.getMenu().add("Tehirizo ny pejy").setOnMenuItemClickListener(item -> { addBookmark(); return true; });
        menu.getMenu().add("Zarao ny pejy").setOnMenuItemClickListener(item -> { shareCurrentPage(); return true; });
        menu.getMenu().add("Fafao ny tantara sy cache").setOnMenuItemClickListener(item -> { confirmClearData(); return true; });
        menu.getMenu().add("Momba an'i Nanou Browser").setOnMenuItemClickListener(item -> { showAbout(); return true; });
        menu.show();
    }

    private SharedPreferences prefs() {
        return getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private JSONArray readArray(String key) {
        try { return new JSONArray(prefs().getString(key, "[]")); }
        catch (Exception ignored) { return new JSONArray(); }
    }

    private void writeArray(String key, JSONArray value) {
        prefs().edit().putString(key, value.toString()).apply();
    }

    private boolean isBookmarked(String url) {
        if (url == null || BrowserUrlResolver.HOME_URL.equals(url)) return false;
        JSONArray data = readArray(KEY_BOOKMARKS);
        for (int i = 0; i < data.length(); i++) {
            JSONObject entry = data.optJSONObject(i);
            if (entry != null && url.equals(entry.optString("url"))) return true;
        }
        return false;
    }

    private void addBookmark() {
        if (activeTab == null || !BrowserUrlResolver.isWebUrl(activeTab.url)) {
            toast("Sokafy aloha ilay tranonkala tehirizina.");
            return;
        }
        JSONArray data = readArray(KEY_BOOKMARKS);
        for (int i = 0; i < data.length(); i++) {
            JSONObject e = data.optJSONObject(i);
            if (e != null && activeTab.url.equals(e.optString("url"))) {
                toast("Efa ao amin'ny tsoratadidy.");
                return;
            }
        }
        JSONObject item = new JSONObject();
        try {
            item.put("title", activeTab.title);
            item.put("url", activeTab.url);
            item.put("savedAt", System.currentTimeMillis());
            data.put(item);
            writeArray(KEY_BOOKMARKS, data);
            updateChrome();
            toast("Voatahiry ny tsoratadidy.");
        } catch (Exception ignored) {
            toast("Tsy voatahiry ny tsoratadidy.");
        }
    }

    private void showBookmarks() {
        showSavedList(KEY_BOOKMARKS, "Tsoratadidy");
    }

    private void showHistory() {
        showSavedList(KEY_HISTORY, "Tantara");
    }

    private void showSavedList(String key, String title) {
        JSONArray data = readArray(key);
        if (data.length() == 0) {
            toast("Mbola tsy misy " + title.toLowerCase(Locale.ROOT) + ".");
            return;
        }
        List<JSONObject> entries = new ArrayList<>();
        for (int i = data.length() - 1; i >= 0; i--) {
            JSONObject obj = data.optJSONObject(i);
            if (obj != null) entries.add(obj);
        }
        String[] labels = new String[entries.size()];
        for (int i = 0; i < entries.size(); i++) {
            JSONObject obj = entries.get(i);
            labels[i] = truncate(obj.optString("title", "Pejy"), 42)
                    + "\n" + truncate(obj.optString("url", ""), 64);
        }
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setItems(labels, (dialog, which) -> {
                    String url = entries.get(which).optString("url", "");
                    if (BrowserUrlResolver.isWebUrl(url) && activeTab != null) loadUrl(activeTab, url);
                })
                .setNegativeButton("Hidio", null)
                .setNeutralButton("Fafao", (dialog, which) -> new AlertDialog.Builder(this)
                        .setMessage("Fafàna avokoa ve ny " + title.toLowerCase(Locale.ROOT) + "?")
                        .setNegativeButton("Aoka", null)
                        .setPositiveButton("Fafao", (d, w) -> {
                            writeArray(key, new JSONArray());
                            toast("Voafafa.");
                        }).show())
                .show();
    }

    private void addHistory(String title, String url) {
        if (!BrowserUrlResolver.isWebUrl(url)) return;
        JSONArray existing = readArray(KEY_HISTORY);
        JSONArray next = new JSONArray();
        JSONObject entry = new JSONObject();
        try {
            entry.put("title", title == null ? hostLabel(url) : title);
            entry.put("url", url);
            entry.put("visitedAt", System.currentTimeMillis());
            next.put(entry);
            for (int i = 0; i < existing.length() && next.length() < MAX_HISTORY; i++) {
                JSONObject old = existing.optJSONObject(i);
                if (old == null || url.equals(old.optString("url"))) continue;
                next.put(old);
            }
            writeArray(KEY_HISTORY, next);
        } catch (Exception ignored) {}
    }

    private void shareCurrentPage() {
        if (activeTab == null || !BrowserUrlResolver.isWebUrl(activeTab.url)) {
            toast("Tsy misy pejy azo zaraina.");
            return;
        }
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_TEXT, activeTab.url);
        share.putExtra(Intent.EXTRA_SUBJECT, activeTab.title);
        startActivity(Intent.createChooser(share, "Zarao amin'ny…"));
    }

    private void confirmClearData() {
        new AlertDialog.Builder(this)
                .setTitle("Fafao ny data")
                .setMessage("Ho voafafa ny tantara sy ny cache ary ny cookies rehetra amin'ity navigateur ity. Hijanona ny tsoratadidy.")
                .setNegativeButton("Aoka", null)
                .setPositiveButton("Fafao", (d, w) -> {
                    writeArray(KEY_HISTORY, new JSONArray());
                    for (BrowserTab tab : tabs) {
                        tab.webView.clearHistory();
                        tab.webView.clearCache(true);
                        tab.webView.clearFormData();
                    }
                    CookieManager.getInstance().removeAllCookies(null);
                    CookieManager.getInstance().flush();
                    android.webkit.WebStorage.getInstance().deleteAllData();
                    toast("Voafafa ny tantara sy ny cache.");
                    if (activeTab != null) statusText.setText("Voafafa ny data local.");
                }).show();
    }

    private void showAbout() {
        new AlertDialog.Builder(this)
                .setTitle("Nanou Browser 1.0.0")
                .setMessage("Navigateur Android maivana misy onglet, fikarohana DuckDuckGo, tsoratadidy, tantara local ary download.\n\nTsy misy analytics na kaonty ilaina. Ny pejy tsidihina dia mbola afaka mampiasa cookies araka ny politika an'ny tranonkala.")
                .setPositiveButton("OK", null)
                .show();
    }

    private void requestDownload(String url, String userAgent, String disposition, String mime) {
        if (!BrowserUrlResolver.isWebUrl(url)) {
            toast("Nosakanana ny download tsy azo antoka.");
            return;
        }
        pendingDownloadUrl = url;
        pendingDownloadAgent = userAgent;
        pendingDownloadDisposition = disposition;
        pendingDownloadMime = mime;
        if (Build.VERSION.SDK_INT <= 28
                && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    STORAGE_PERMISSION_REQUEST);
            return;
        }
        startDownload();
    }

    private void startDownload() {
        if (pendingDownloadUrl == null) return;
        try {
            DownloadManager manager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(pendingDownloadUrl));
            String filename = URLUtil.guessFileName(pendingDownloadUrl,
                    pendingDownloadDisposition, pendingDownloadMime);
            request.setTitle(filename);
            request.setDescription("Nanou Browser");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename);
            if (pendingDownloadAgent != null && !pendingDownloadAgent.isEmpty()) {
                request.addRequestHeader("User-Agent", pendingDownloadAgent);
            }
            String cookies = CookieManager.getInstance().getCookie(pendingDownloadUrl);
            if (cookies != null && !cookies.isEmpty()) request.addRequestHeader("Cookie", cookies);
            manager.enqueue(request);
            toast("Nanomboka ny download. Jereo ny Downloads.");
        } catch (Exception e) {
            toast("Tsy afaka nanomboka ny download. Jereo ny permission.");
        } finally {
            pendingDownloadUrl = null;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == STORAGE_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startDownload();
            } else {
                toast("Ilaina ny permission storage mba hisintonana rakitra amin'ity Android ity.");
                pendingDownloadUrl = null;
            }
        }
    }

    private String hostLabel(String url) {
        try {
            String host = URI.create(url).getHost();
            return host == null ? url : host.replaceFirst("^www\\.", "");
        } catch (Exception ignored) {
            return url;
        }
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (activeTab != null && activeTab.webView.canGoBack()) {
                activeTab.webView.goBack();
                return true;
            }
            if (tabs.size() > 1 && activeTab != null) {
                closeTab(activeTab);
                return true;
            }
            openHome();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void openHome() {
        if (activeTab == null) addTab(true);
        else loadHome(activeTab);
    }

    private void goBack() {
        if (activeTab != null && activeTab.webView.canGoBack()) activeTab.webView.goBack();
        else openHome();
    }

    private void goForward() {
        if (activeTab != null && activeTab.webView.canGoForward()) activeTab.webView.goForward();
    }

    private void reload() {
        if (activeTab == null) return;
        if (BrowserUrlResolver.isHomeUrl(activeTab.url)) loadHome(activeTab);
        else activeTab.webView.reload();
    }

    @Override
    protected void onDestroy() {
        for (BrowserTab tab : new ArrayList<>(tabs)) {
            try {
                tab.webView.stopLoading();
                if (tab.webView.getParent() instanceof ViewGroup) {
                    ((ViewGroup) tab.webView.getParent()).removeView(tab.webView);
                }
                tab.webView.destroy();
            } catch (Throwable ignored) {}
        }
        tabs.clear();
        super.onDestroy();
    }
}
