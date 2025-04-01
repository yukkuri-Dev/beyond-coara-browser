package com.coara.browser;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.webkit.CookieManager;
import android.webkit.PermissionRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewDatabase;
import androidx.appcompat.app.AppCompatActivity;

public class IncognitoMainActivity extends MainActivity {
    private boolean isIncognitoMode = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setBackgroundColor(Color.DKGRAY);
        handleIntent(getIntent());
    }

    @Override
    protected WebView createNewWebView() {
        WebView webView = super.createNewWebView();
        if (isIncognitoMode) {
            WebSettings settings = webView.getSettings();
            settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
            settings.setSaveFormData(false);
            settings.setGeolocationEnabled(false);
            CookieManager.getInstance().setAcceptCookie(false);
        }
        return webView;
    }

  
    @Override
    public void onPageFinished(WebView view, String url) {
        super.onPageFinished(view, url);
        
        if (!isIncognitoMode) {
            addHistory(url, view.getTitle());
        }
    }


    @Override
    protected void onPause() {
        super.onPause();
        if (isIncognitoMode) {
            for (WebView webView : webViews) {
                webView.clearHistory();        
                webView.clearCache(true);    
            }
          
            CookieManager.getInstance().removeAllCookies(null);
            CookieManager.getInstance().flush();
        
            WebViewDatabase.getInstance(this).clearFormData();
        }
    }

    @Override
    public void onPermissionRequest(final PermissionRequest request) {
        if (isIncognitoMode) {
            request.deny(); 
        } else {
            super.onPermissionRequest(request);
        }
    }

    @Override
    protected void handleIntent(Intent intent) {
        super.handleIntent(intent);
        if (intent != null && Intent.ACTION_VIEW.equals(intent.getAction())) {
            String url = intent.getDataString();
            if (url != null) {
                WebView webView = createNewWebView();
                webView.loadUrl(url);
                addTab(webView);
            }
        }
    }
}
