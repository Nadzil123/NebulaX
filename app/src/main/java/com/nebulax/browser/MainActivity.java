package com.nebulax.browser;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.view.View;

public class MainActivity extends Activity {

    WebView webView;
    EditText urlInput;
    Button goButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webView);
        urlInput = findViewById(R.id.urlInput);
        goButton = findViewById(R.id.goButton);

        webView.setWebViewClient(new WebViewClient());
        webView.getSettings().setJavaScriptEnabled(true);

        goButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String url = urlInput.getText().toString();
                if (!url.startsWith("http")) {
                    url = "https://" + url;
                }
                webView.loadUrl(url);
            }
        });

        webView.loadUrl("https://www.google.com");
    }
}
