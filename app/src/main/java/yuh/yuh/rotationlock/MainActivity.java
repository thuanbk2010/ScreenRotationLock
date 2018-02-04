package yuh.yuh.rotationlock;

import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.SystemClock;
import android.support.annotation.DrawableRes;
import android.support.annotation.IdRes;
import android.support.annotation.RequiresApi;
import android.support.annotation.StringRes;
import android.support.design.widget.AppBarLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.GravityCompat;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewCompat;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.provider.Settings;
import android.support.v7.app.AppCompatDelegate;
import android.support.v7.widget.AppCompatTextView;
import android.support.v7.widget.Toolbar;
import android.text.Html;
import android.text.Spanned;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import java.util.concurrent.atomic.AtomicInteger;

import static android.view.Surface.*;

/**
 * MainActivity. There's no need any more explanation.
 */
public class MainActivity extends AppCompatActivity implements
        View.OnClickListener, View.OnLongClickListener, SettingsContentObserver.OnSettingsChangeListener {

    public static final String PREF_FILE_NAME = "prefs";
    public static final AtomicInteger sNextGeneratedId = new AtomicInteger();
    public static final int HIDE_OR_SHOW_APP_ICON_ID = generateNewId();

    private enum FAB_ACTION { REQUEST_PERMISSION, PERFORM_LOCK }

    /**
     * Check if Quick Mode is enabled.
     */
    private boolean quickMode;

    /**
     * Field members
     */
    private TextView mQuickMsg;
    private FloatingActionButton mFab;
    private AppCompatTextView mHelp;
    private AlertDialog mDialog;
    private SharedPreferences mPreferences;
    private ColorStateList mFabColorLocked, mFabColorUnlocked;
    private ContentResolver mContentResolver;
    private SettingsContentObserver mSettingsContentObserver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mPreferences = getSharedPreferences(PREF_FILE_NAME, MODE_PRIVATE);
        mContentResolver = getContentResolver();

        // Quick mode
        quickMode = mPreferences.getBoolean("qm", false);

        // If Quick mode is enabled, then quickly lock orientation to defined value, the default is 90 degree (landscape).
        // We don't need any UI here. So, we call finish() and return.
        if (!isOrientationLocked() && quickMode && savedInstanceState == null && canWriteSettings()) {
            int qmo = mPreferences.getInt("qmo", ROTATION_90);
            Settings.System.putInt(mContentResolver, Settings.System.ACCELEROMETER_ROTATION,  0);
            Settings.System.putInt(mContentResolver, Settings.System.USER_ROTATION, qmo);
            showLongToast(this, getString(R.string.quick_mode_toast, getResources().getStringArray(R.array.quick_mode_options)[qmo]));
            finish();
            return;
        }

        // UI preparation and register Observer.
        setContentView(R.layout.activity_main);
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }

        Toolbar toolbar = findViewById(R.id.toolbar);
//        toolbar.setNavigationIcon(R.drawable.app_icon);
        toolbar.setOverflowIcon(getResources().getDrawable(R.drawable.ic_more));
        setSupportActionBar(toolbar);
        getSupportActionBar().setTitle(R.string.app_name);

        mQuickMsg = findViewById(R.id.quick_msg);
        mHelp = findViewById(R.id.help);
        mFab  = findViewById(R.id.fab);

        mFab.setOnClickListener(this);
        mFab.setOnLongClickListener(this);
        mFabColorLocked = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.colorPrimary));
        mFabColorUnlocked = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.colorSecondary));

        mQuickMsg.setVisibility(View.GONE);
        mSettingsContentObserver = new SettingsContentObserver(this, new Handler());
        mSettingsContentObserver.setOnSettingsChangeListener(this);

        showStartupDialog();
    }

    /**
     * Depends on whether user has granted {@link android.Manifest.permission#WRITE_SETTINGS}, the functionality of {@link #mFab}
     * and the content of {@link #mHelp} will be different.
     * @see #canWriteSettings()
     * @see #onClick(View)
     */
    @Override
    protected void onStart() {
        super.onStart();
        if (canWriteSettings()) {
            mContentResolver.registerContentObserver(Settings.System.CONTENT_URI, true, mSettingsContentObserver);
            mHelp.setText(quickMode ? R.string.quick_mode_is_on : R.string.quick_help);
            mFab.setTag(FAB_ACTION.PERFORM_LOCK);
            onSettingChange(null);
        } else {
            mHelp.setText(R.string.request_permission);
            mFab.setBackgroundTintList(mFabColorLocked);
            mFab.setImageResource(R.drawable.ic_settings);
            mFab.setTag(FAB_ACTION.REQUEST_PERMISSION);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        mContentResolver.unregisterContentObserver(mSettingsContentObserver);
        if (mDialog != null && mDialog.isShowing()) {
            mDialog.dismiss();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mDialog != null && mDialog.isShowing()) {
            mDialog.dismiss();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        if (Build.VERSION.SDK_INT >= 24) {
            boolean isAppIconHidden = mPreferences.getBoolean("ha", false);
            menu.add(0, HIDE_OR_SHOW_APP_ICON_ID, 0, isAppIconHidden ? R.string.show_app_icon : R.string.hide_app_icon);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int i = item.getItemId();
//        if (i == android.R.id.home) showAboutDialog();
        if (i == R.id.yuhapps) showAboutDialog();
        else if (i == R.id.help) startActivity(new Intent(this, HelpActivity.class));
        else if (i == R.id.quick_mode) showQuickModeDialog();
        else if (i == HIDE_OR_SHOW_APP_ICON_ID) {
            boolean t = toggleAppIcon();
            showMessage(t ? R.string.app_icon_is_hidden_in_launcher : R.string.app_icon_is_shown_in_launcher, t);
            item.setTitle(t ? R.string.show_app_icon : R.string.hide_app_icon);
        }
        return true;
    }

    /**
     * Invoked when {@link #mFab} is clicked.
     * Depends on whether user has granted {@link android.Manifest.permission#WRITE_SETTINGS}.
     * @param view The mFab itself.
     * @see #onStart()
     * @see #canWriteSettings()
     */
    @Override
    public void onClick(View view) {
        Object tag = view.getTag();
        // If WRITE_SETTINGS permission is not granted.
        if (tag.equals(FAB_ACTION.REQUEST_PERMISSION)) {
            try {
                @SuppressLint("InlinedApi")
                Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_WRITE_SETTINGS);
                intent.setData(Uri.parse("package:" + this.getPackageName()));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            } catch (ActivityNotFoundException e) {
                // Unsupported devices, such as Android TV or Watch.
                showMessage(R.string.device_not_supported);
            }
        }
        // If the permission is granted.
        else if (tag.equals(FAB_ACTION.PERFORM_LOCK)) {
            final boolean locked = isOrientationLocked();
            final int rotation = getWindowManager().getDefaultDisplay().getRotation();
            Settings.System.putInt(mContentResolver, Settings.System.ACCELEROMETER_ROTATION, locked ? 1 : 0);
            Settings.System.putInt(mContentResolver, Settings.System.USER_ROTATION, locked ? ROTATION_0 : rotation);
        }
    }

    @Override
    public boolean onLongClick(View v) {
        return true;
    }

    /**
     * Invoked everytime a setting is changed.
     * @see SettingsContentObserver.OnSettingsChangeListener
     */
    @Override
    public void onSettingChange(Context context) {
        boolean lock = isOrientationLocked();
        showMessage(lock);
    }

    private boolean canWriteSettings() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.System.canWrite(this);
    }

    @RequiresApi(24)
    private boolean toggleAppIcon() {
        boolean isAppIconHidden = mPreferences.getBoolean("ha", false);
        PackageManager pm = this.getPackageManager();
        ComponentName name = new ComponentName(getApplicationContext(), MainActivity.class);
        pm.setComponentEnabledSetting(name, isAppIconHidden ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                : PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
        mPreferences.edit().putBoolean("ha", !isAppIconHidden).apply();
        return !isAppIconHidden;
    }

    /**
     * Check if screen orientation is locked or unlocked.
     */
    private boolean isOrientationLocked() {
        switch (Settings.System.getInt(mContentResolver, Settings.System.ACCELEROMETER_ROTATION, -1)) {
            case 0:
                return true;
            case 1:
                return false;
            default:
                throw new IllegalStateException("Screen orientation cannot be detected");
        }
    }

    /**
     * Show message.
     * @see #mQuickMsg
     * @param locked see {@link #isOrientationLocked()}
     */
    private void showMessage(final boolean locked) {
        mFab.hide();
        mQuickMsg.setText(locked ? R.string.rotation_locked_long : R.string.rotation_unlocked_long);
        mQuickMsg.setVisibility(View.VISIBLE);
        Animation appear = AnimationUtils.loadAnimation(this, R.anim.fade_in);
        appear.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {

            }

            @Override
            public void onAnimationEnd(Animation animation) {
                SystemClock.sleep(375);
                Animation disappear = AnimationUtils.loadAnimation(MainActivity.this, R.anim.fade_out);
                disappear.setAnimationListener(new Animation.AnimationListener() {
                    @Override
                    public void onAnimationStart(Animation animation) {

                    }

                    @Override
                    public void onAnimationEnd(Animation animation) {
                        mQuickMsg.setVisibility(View.GONE);
                        mFab.setBackgroundTintList(locked ? mFabColorLocked : mFabColorUnlocked);
                        mFab.setImageResource(locked ? R.drawable.ic_screen_lock_rotation : R.drawable.ic_screen_rotation_undefined);
                        if (!mFab.isShown()) mFab.show();
                    }

                    @Override
                    public void onAnimationRepeat(Animation animation) {

                    }
                });
                mQuickMsg.setAnimation(disappear);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }
        });
        mQuickMsg.setAnimation(appear);
    }

    /**
     * Show message.
     * @see #mQuickMsg
     */
    private void showMessage(@StringRes int message, final boolean longMessage) {
        mFab.hide();
        mQuickMsg.setText(message);
        mQuickMsg.setVisibility(View.VISIBLE);
        Animation appear = AnimationUtils.loadAnimation(this, R.anim.fade_in);
        appear.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {

            }

            @Override
            public void onAnimationEnd(Animation animation) {
                SystemClock.sleep(longMessage ? 750 :375);
                Animation disappear = AnimationUtils.loadAnimation(MainActivity.this, R.anim.fade_out);
                disappear.setAnimationListener(new Animation.AnimationListener() {
                    @Override
                    public void onAnimationStart(Animation animation) {

                    }

                    @Override
                    public void onAnimationEnd(Animation animation) {
                        mQuickMsg.setVisibility(View.GONE);
                        if (!mFab.isShown()) mFab.show();
                    }

                    @Override
                    public void onAnimationRepeat(Animation animation) {

                    }
                });
                mQuickMsg.setAnimation(disappear);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }
        });
        mQuickMsg.setAnimation(appear);
    }

    private void showMessage(@StringRes int message) {
        showMessage(message, false);
    }

    /**
     * @return true if this is the first time install, false if user updates the app.
     */
    private boolean isFirstInstall() {
        try {
            PackageInfo info      =   getPackageManager().getPackageInfo(getPackageName(), 0);
            long firstInstallTime =   info.firstInstallTime;
            long lastUpdateTime   =   info.lastUpdateTime;
            return firstInstallTime == lastUpdateTime;
        } catch (PackageManager.NameNotFoundException ignored) {
            return false;
        }
    }

    private void showStartupDialog() {
        final String vc = "vc";
        final SharedPreferences.Editor editor = mPreferences.edit();
        int lastInstalledVersion = mPreferences.getInt(vc, 0);
        if (lastInstalledVersion < BuildConfig.VERSION_CODE - 1) {
            boolean temp = isFirstInstall();
            AlertDialog.Builder dialog = new AlertDialog.Builder(this)
                    .setTitle(R.string.thanks)
                    .setMessage(temp
                        ? R.string.thanks_first
                        : R.string.thanks_old)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                                editor.putInt(vc, BuildConfig.VERSION_CODE).apply();
                        }
            });
            mDialog = dialog.show();
        }
    }

    private void showAboutDialog() {
        AlertDialog.Builder dialog = new AlertDialog.Builder(this)
                .setTitle(getString(R.string.app_name) + " " + BuildConfig.VERSION_NAME + "(" + BuildConfig.VERSION_CODE + ")")
                .setMessage(R.string.about)
                .setNegativeButton(R.string.close, null)
                .setNeutralButton("Google Play", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        try {
                            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=yuh.yuh.rotationlock")));
                        } catch (ActivityNotFoundException exception) {
                            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=yuh.yuh.rotationlock")));
                        }
                    }
                });
        mDialog = dialog.show();
    }

    /**
     * Quick mode dialog
     */
    private void showQuickModeDialog() {
        final SharedPreferences.Editor editor = mPreferences.edit();
        AlertDialog.Builder dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.quick_mode)
                .setMessage(getString(R.string.quick_mode_message,
                        getResources().getStringArray(R.array.quick_mode_options)[mPreferences.getInt("qmo", ROTATION_90)],
                        quickMode ? getString(R.string.enabled) : getString(R.string.disabled)))
                .setPositiveButton(quickMode ? R.string.disable : R.string.enable, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        editor.putBoolean("qm", !quickMode).apply();
                        showMessage(quickMode ? R.string.quick_mode_is_disabled : R.string.quick_mode_is_enabled);
                        mHelp.setText(quickMode ? R.string.quick_help : R.string.quick_mode_is_on);
                        quickMode = !quickMode;
                    }
                })
                .setNeutralButton(R.string.change_mode, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        showQuickModeOptionsDialog();
                    }
                });
        mDialog = dialog.show();
    }

    /**
     * Quick mode options dialog
     */
    private void showQuickModeOptionsDialog() {
        final int qmo = mPreferences.getInt("qmo", ROTATION_90);
        final int[] nqmo = new int[] {qmo};
        final SharedPreferences.Editor editor = mPreferences.edit();
        AlertDialog.Builder dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.quick_mode)
                .setSingleChoiceItems(R.array.quick_mode_options, qmo, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        nqmo[0] = which;
                    }
                })
                .setPositiveButton(R.string.okay, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        editor.putInt("qmo", nqmo[0]).apply();
                        showMessage(R.string.saved);
                    }
                })
                .setNegativeButton(R.string.close, null);
        mDialog = dialog.show();
    }

    /**
     * Show Toast. The Toast is heavily customised.
     */
    private void showToast(CharSequence message, int gravity) {
        Toast toast = new Toast(this);
        View view = getLayoutInflater().inflate(R.layout.transient_notification, null);
        TextView text = view.findViewById(R.id.message);
        text.setText(message);
        toast.setView(view);
        toast.setDuration(Toast.LENGTH_SHORT);
        toast.setGravity(gravity, 0, 0);
        toast.show();
    }

    private void showToast(@StringRes int message, int gravity) {
        showToast(getString(message), gravity);
    }

    private void showToast(CharSequence message) {
        Toast toast = new Toast(this);
        View view = getLayoutInflater().inflate(R.layout.transient_notification, null);
        TextView text = view.findViewById(R.id.message);
        text.setText(message);
        toast.setView(view);
        toast.setDuration(Toast.LENGTH_SHORT);
        toast.show();
    }

    private void showToast(@StringRes int message) {
        showToast(getString(message));
    }

    private void showLongToast(CharSequence message) {
        Toast toast = new Toast(this);
        View view = getLayoutInflater().inflate(R.layout.transient_notification, null);
        TextView text = view.findViewById(R.id.message);
        text.setText(message);
        toast.setView(view);
        toast.setDuration(Toast.LENGTH_SHORT);
        toast.show();
    }

    private void showLongToast(@StringRes int message) {
        showLongToast(this, getString(message));
    }

    static void showToast(Context context, CharSequence message) {
        Toast toast = new Toast(context);
        View view = LayoutInflater.from(context).inflate(R.layout.transient_notification, null);
        TextView text = view.findViewById(R.id.message);
        text.setText(message);
        toast.setView(view);
        toast.setDuration(Toast.LENGTH_SHORT);
        toast.show();
    }

    static void showToast(Context context, @StringRes int message) {
        showToast(context, context.getString(message));
    }

    static void showLongToast(Context context, CharSequence message) {
        Toast toast = new Toast(context);
        View view = LayoutInflater.from(context).inflate(R.layout.transient_notification, null);
        TextView text = view.findViewById(R.id.message);
        text.setText(message);
        toast.setView(view);
        toast.setDuration(Toast.LENGTH_LONG);
        toast.show();
    }

    static void showLongToast(Context context, @StringRes int message) {
        showLongToast(context, context.getText(message));
    }

    public static int generateNewId() {
        while (true) {
            final int result = sNextGeneratedId.get();
            // aapt-generated IDs have the high byte nonzero; clamp to the range under that.
            int newValue = result + 1;
            if (newValue > 0x00FFFFFF) newValue = 1; // Roll over to 1, not 0.
            if (sNextGeneratedId.compareAndSet(result, newValue)) {
                return result;
            }
        }
    }
    
}
