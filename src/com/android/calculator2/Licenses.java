package com.android.calculator2;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebView;

public class Licenses extends Activity {

    private static final String LICENSE_URL = "file:///android_asset/licenses.html";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final WebView webView = new WebView(this);
        webView.loadUrl(LICENSE_URL);

        setContentView(webView);
    }
}
