package com.example.inigrowx;

import android.graphics.Color;
import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Transparent status bar
        getWindow().setStatusBarColor(Color.TRANSPARENT);

        webView = findViewById(R.id.webView);

        WebSettings webSettings = webView.getSettings();

        // WebView settings
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setUseWideViewPort(true);
        webSettings.setSupportZoom(false);
        webSettings.setAllowFileAccess(true);
        webSettings.setAllowContentAccess(true);

        // Open links inside WebView
        webView.setWebViewClient(new WebViewClient());

        // Load website
        webView.loadUrl("https://inigrowx.netlify.app/");
    }

    // Handle Android Back Button
    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}