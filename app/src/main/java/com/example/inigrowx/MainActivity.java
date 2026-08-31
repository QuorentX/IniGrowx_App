package com.example.inigrowx;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private boolean loginScrollLocked;
    private float loginTouchStartY;

    private static final String APP_USER_AGENT_SUFFIX = " inigrowx-app/1.0";
    private static final String WEBSITE_URL = "https://inigrowx.in/login?app=1";
    private static final String WEB_AUTH_CALLBACK = "https://inigrowx.in/auth/callback";
    private static final String CUSTOM_SCHEME = "inigrowx";
    private static final String CUSTOM_AUTH_HOST = "auth";
    private static final String CUSTOM_CALLBACK_PATH = "/callback";

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
        applySoftInputModeForUrl(WEBSITE_URL);
        webView = findViewById(R.id.webView);

        WebSettings settings = webView.getSettings();

        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setSupportZoom(false);
        settings.setLoadsImagesAutomatically(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setSupportMultipleWindows(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setUserAgentString(settings.getUserAgentString() + APP_USER_AGENT_SUFFIX);

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, true);

        webView.setOnTouchListener((v, event) -> {
            if (!loginScrollLocked) {
                return false;
            }

            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    loginTouchStartY = event.getY();
                    return false;
                case MotionEvent.ACTION_MOVE:
                    return Math.abs(event.getY() - loginTouchStartY) > 12f;
                default:
                    return false;
            }
        });

        webView.setWebChromeClient(new WebChromeClient());

        webView.setWebViewClient(new WebViewClient() {

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();

                if (isCustomSchemeCallback(uri)) {
                    completeOAuthInWebView(uri);
                    return true;
                }

                return false;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                applySoftInputModeForUrl(url);
                applyLoginScrollLock(url);

                if (isLoginPageUrl(url)) {
                    view.evaluateJavascript(
                            "(function(){var m=document.querySelector('meta[name=viewport]');"
                                    + "if(!m||m.dataset.igxKb)return;"
                                    + "m.content=(m.content||'').replace("
                                    + "'interactive-widget=resizes-content',"
                                    + "'interactive-widget=overlays-content');"
                                    + "m.dataset.igxKb='1';})();",
                            null);
                }
            }

            @Override
            public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
                super.doUpdateVisitedHistory(view, url, isReload);
                applySoftInputModeForUrl(url);
                applyLoginScrollLock(url);
            }
        });

        if (savedInstanceState == null) {
            handleIncomingUri(getIntent().getData(), true);
        } else {
            webView.restoreState(savedInstanceState);
        }

        getOnBackPressedDispatcher().addCallback(this,
                new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        if (webView.canGoBack()) {
                            webView.goBack();
                        } else {
                            finish();
                        }
                    }
                });
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIncomingUri(intent.getData(), false);
    }

    private void applyLoginScrollLock(String url) {
        if (webView == null) {
            return;
        }

        loginScrollLocked = isLoginPageUrl(url);

        if (loginScrollLocked) {
            webView.setVerticalScrollBarEnabled(false);
            webView.setHorizontalScrollBarEnabled(false);
            webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
            webView.scrollTo(0, 0);
        } else {
            webView.setVerticalScrollBarEnabled(true);
            webView.setHorizontalScrollBarEnabled(true);
            webView.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        }
    }

    private void applySoftInputModeForUrl(String url) {
        int mode = isLoginPageUrl(url)
                ? WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
                : WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
        getWindow().setSoftInputMode(mode);
    }

    private static boolean isLoginPageUrl(String url) {
        if (url == null) {
            return false;
        }

        Uri uri = Uri.parse(url);
        if (!isAllowedHttpsHost(uri.getHost())) {
            return false;
        }

        String path = uri.getPath();
        if (path == null) {
            return false;
        }

        return path.startsWith("/login") || path.startsWith("/register");
    }

    private void handleIncomingUri(Uri uri, boolean isColdStart) {
        if (uri != null && isOAuthCallbackUri(uri)) {
            completeOAuthInWebView(uri);
            return;
        }

        if (isColdStart) {
            webView.loadUrl(WEBSITE_URL);
        }
    }

    private void completeOAuthInWebView(Uri callbackUri) {
        String error = callbackUri.getQueryParameter("error");
        if (error != null) {
            Uri.Builder errorUrl = Uri.parse("https://inigrowx.in/auth/error").buildUpon()
                    .appendQueryParameter("app", "1")
                    .appendQueryParameter("error", error);

            String errorDescription = callbackUri.getQueryParameter("error_description");
            if (errorDescription != null) {
                errorUrl.appendQueryParameter("error_description", errorDescription);
            }

            webView.loadUrl(errorUrl.build().toString());
            return;
        }

        webView.loadUrl(buildWebSessionUrl(callbackUri));
    }

    private static String buildWebSessionUrl(Uri callbackUri) {
        Uri.Builder builder = Uri.parse(WEB_AUTH_CALLBACK).buildUpon()
                .appendQueryParameter("app", "1");

        for (String name : callbackUri.getQueryParameterNames()) {
            String value = callbackUri.getQueryParameter(name);
            if (value != null) {
                builder.appendQueryParameter(name, value);
            }
        }

        String fragment = callbackUri.getFragment();
        if (!TextUtils.isEmpty(fragment)) {
            return builder.build().toString() + "#" + fragment;
        }

        return builder.build().toString();
    }

    private static boolean isCustomSchemeCallback(Uri uri) {
        if (uri == null || !CUSTOM_SCHEME.equals(uri.getScheme())) {
            return false;
        }

        if (!CUSTOM_AUTH_HOST.equals(uri.getHost())) {
            return false;
        }

        String path = uri.getPath();
        return path != null && path.startsWith(CUSTOM_CALLBACK_PATH);
    }

    private static boolean isAllowedHttpsHost(String host) {
        return "inigrowx.in".equals(host) || "www.inigrowx.in".equals(host);
    }

    private static boolean isHttpsOAuthCallback(Uri uri) {
        if (uri == null || !"https".equals(uri.getScheme())) {
            return false;
        }

        if (!isAllowedHttpsHost(uri.getHost())) {
            return false;
        }

        String path = uri.getPath();
        if (path == null) {
            return false;
        }

        return path.startsWith("/auth/callback") || path.startsWith("/auth/app-return");
    }

    private static boolean hasOAuthCallbackParams(Uri uri) {
        if (uri.getQueryParameter("error") != null
                || uri.getQueryParameter("code") != null
                || uri.getQueryParameter("access_token") != null
                || uri.getQueryParameter("refresh_token") != null) {
            return true;
        }

        String fragment = uri.getFragment();
        if (fragment == null) {
            return false;
        }

        return fragment.contains("access_token=")
                || fragment.contains("refresh_token=")
                || fragment.contains("code=")
                || fragment.contains("error=");
    }

    private static boolean isOAuthCallbackUri(Uri uri) {
        if (uri == null || !hasOAuthCallbackParams(uri)) {
            return false;
        }

        return isCustomSchemeCallback(uri) || isHttpsOAuthCallback(uri);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        if (webView != null) {
            webView.saveState(outState);
        }
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.destroy();
        }

        super.onDestroy();
    }
}
