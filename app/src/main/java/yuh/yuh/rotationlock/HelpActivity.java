package yuh.yuh.rotationlock;

import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.app.AppCompatDelegate;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.WebView;
import android.widget.Toast;

/**
 * HelpActivity. No need any further explanation.
 */
public class HelpActivity extends AppCompatActivity {

    private WebView mWebView;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_help);
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }

        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationIcon(R.drawable.ic_back);
        setSupportActionBar(toolbar);
        getSupportActionBar().setTitle(R.string.activity_help);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        mWebView = findViewById(R.id.content);
        if (savedInstanceState == null) {
            mWebView.loadUrl("file:///android_asset/help/index.html");
        } else {
            mWebView.restoreState(savedInstanceState);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            getMenuInflater().inflate(R.menu.menu_help, menu);
        }
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                break;
            case R.id.hide_or_show_app:
                Toast.makeText(this, toggleAppIcon() ? R.string.app_icon_is_hidden_in_launcher : R.string.app_icon_is_shown_in_launcher, 1).show();
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mWebView.saveState(outState);
    }

    @RequiresApi(24)
    private boolean toggleAppIcon() {
        SharedPreferences preferences = getSharedPreferences(MainActivity.PREF_FILE_NAME, MODE_PRIVATE);
        boolean isAppIconHidden = preferences.getBoolean("ha", false);
        PackageManager pm = this.getPackageManager();
        ComponentName name = new ComponentName(getApplicationContext(), MainActivity.class);
        pm.setComponentEnabledSetting(name, isAppIconHidden ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                : PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
        preferences.edit().putBoolean("ha", !isAppIconHidden).apply();
        return !isAppIconHidden;
    }
}
