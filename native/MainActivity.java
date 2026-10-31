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
        w.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        w.setStatusBarColor(t);
        w.setNavigationBarColor(bt);
        try {
            getBridge().getWebView().setBackgroundColor(t);
        } catch (Exception e) { }
    }
}