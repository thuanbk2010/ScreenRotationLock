package yuh.yuh.rotationlock;

import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
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
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import static android.view.Surface.*;

/**
 * MainActivity. There's no need any more explanation.
 */
public class MainActivity extends AppCompatActivity implements
        View.OnClickListener, View.OnLongClickListener, SettingsContentObserver.OnSettingsChangeListener {

    public static final String PREF_FILE_NAME = "prefs";

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
            showLongToast(String.format(getString(R.string.quick_mode_toast),
                    getResources().getStringArray(R.array.quick_mode_options)[qmo]));
            finish();
            return;
        }

        // UI preparation and register Observer.
        setContentView(R.layout.activity_main);
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);

        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationIcon(R.drawable.app_icon);
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
     * Depends on whether user has granted {@link android.Manifest.permission.WRITE_SETTINGS}, the functionality of {@link #mFab}
     * and the content of {@link #mHelp} will be different.
     * @see #canWriteSettings()
     * @see #onClick(View)
     */
    @Override
    protected void onStart() {
        super.onStart();
        if (canWriteSettings()) {
            mContentResolver.registerContentObserver(Settings.System.CONTENT_URI, true, mSettingsContentObserver);
            mHelp.setText(fromHtml(quickMode ? getString(R.string.quick_mode_is_on) : getString(R.string.quick_help)));
            mFab.setTag(FAB_ACTION.PERFORM_LOCK);
            onSettingChange(null);
        } else {
            mHelp.setText(fromHtml(getString(R.string.request_permission)));
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
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                showAboutDialog();
                break;
            case R.id.yuhapps:
                showAboutDialog();
                break;
            case R.id.help:
                startActivity(new Intent(this, HelpActivity.class));
                break;
            case R.id.quick_mode:
                showQuickModeDialog();
                break;
        }
        return true;
    }

    /**
     * Invoked when {@link #mFab} is clicked.
     * Depends on whether user has granted {@link android.Manifest.permission.WRITE_SETTINGS}.
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

    private Spanned fromHtml(String resource) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return Html.fromHtml(resource, 0);
        } else {
            return Html.fromHtml(resource);
        }
    }

    private Spanned fromHtml(@StringRes int resource) {
        return fromHtml(getString(resource));
    }

    private boolean canWriteSettings() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.System.canWrite(this);
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
     * @param lock see {@link #isOrientationLocked()}
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
    private void showMessage(@StringRes int message) {
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
                SystemClock.sleep(375);
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
                .setMessage(fromHtml(String.format(getString(R.string.quick_mode_message),
                        getResources().getStringArray(R.array.quick_mode_options)[mPreferences.getInt("qmo", ROTATION_90)],
                        quickMode ? getString(R.string.enabled) : getString(R.string.disabled))))
                .setPositiveButton(quickMode ? R.string.disable : R.string.enable, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        editor.putBoolean("qm", !quickMode).apply();
                        showMessage(quickMode ? R.string.quick_mode_is_disabled : R.string.quick_mode_is_enabled);
                        mHelp.setText(fromHtml(quickMode ? R.string.quick_help : R.string.quick_mode_is_on));
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
        showLongToast(getString(message));
    }
}
