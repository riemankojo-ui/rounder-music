package com.rieman.softplayer;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle b) {
        registerPlugin(MusicPlugin.class);
        super.onCreate(b);
        SharedPreferences p = getSharedPreferences("sp", 0);
        int t = p.getInt("top", 0xFF1B1A26);
        int bt = p.getInt("bot", 0xFF0E0E12);
        Window w = getWindow();
        w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(t));
        w.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        w.setStatusBarColor(t);
        w.setNavigationBarColor(bt);
            try {
        getBridge().getWebView().setBackgroundColor(t);
    } catch (Exception e) { }

    if (android.os.Build.VERSION.SDK_INT >= 30 && !android.os.Environment.isExternalStorageManager()) {
        try {
            android.content.Intent intent = new android.content.Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
            intent.setData(android.net.Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } catch (Exception e) { }
    }
}

    @Override
    public void onResume() {
        super.onResume();
        try {
            android.webkit.WebView wv = getBridge().getWebView();
            wv.setBackgroundColor(getSharedPreferences("sp", 0).getInt("top", 0xFF1B1A26));
            wv.onResume();
            wv.resumeTimers();
        } catch (Exception e) { }
    }
}