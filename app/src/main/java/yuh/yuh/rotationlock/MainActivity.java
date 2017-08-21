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
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.content.ContextCompat;
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
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import static android.view.Surface.*;

public class MainActivity extends AppCompatActivity implements
        View.OnClickListener, View.OnLongClickListener, SettingsContentObserver.OnSettingsChangeListener {

    private enum FAB_ACTION { REQUEST_PERMISSION, PERFORM_LOCK }

    private QuickMessage mQuickMsg;
    private FloatingActionButton mFab;
    private TextView mHelp;

    private ContentResolver mContentResolver;
    private SettingsContentObserver mSettingsContentObserver;

    private ColorStateList mFabColorLocked, mFabColorUnlocked;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);

        Toolbar toolbar = bindView(R.id.toolbar);
        toolbar.setNavigationIcon(R.drawable.app_icon);
        setSupportActionBar(toolbar);
        getSupportActionBar().setTitle(R.string.app_name);

        mQuickMsg = bindView(R.id.quick_msg);
        mHelp = bindView(R.id.help);
        mFab  = bindView(R.id.fab);

        mFab.setRippleColor(/*ContextCompat.getColor(this, android.R.color.white)*/ Color.parseColor("#80FFFFFF"));
        mFab.setOnClickListener(this);
        mFab.setOnLongClickListener(this);
        mFabColorLocked = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.colorPrimary));
        mFabColorUnlocked = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.colorSecondary));
        mQuickMsg.setVisibility(View.GONE);
        mContentResolver = getContentResolver();
        mSettingsContentObserver = new SettingsContentObserver(this, new Handler());
        mSettingsContentObserver.setOnSettingsChangeListener(this);

        showStartupDialog();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.System.canWrite(this)) {
            mHelp.setText(fromHtml(getString(R.string.request_permission)));
            mFab.setBackgroundTintList(mFabColorLocked);
            mFab.setImageResource(R.drawable.ic_settings);
            mFab.setTag(FAB_ACTION.REQUEST_PERMISSION);
        } else {
//            rotation = getWindowManager().getDefaultDisplay().getRotation();
            mContentResolver.registerContentObserver(Settings.System.CONTENT_URI, true, mSettingsContentObserver);
            mHelp.setText(fromHtml(getString(R.string.quick_help)));
            mFab.setTag(FAB_ACTION.PERFORM_LOCK);
            onSettingChange(null);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();
        mContentResolver.unregisterContentObserver(mSettingsContentObserver);
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
                MainActivity.this.finish();
                break;
            case R.id.yuhapps:
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://developer?id=YUH+APPS")));
                } catch (ActivityNotFoundException exception) {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/developer?id=YUH+APPS")));
                }
                break;
            case R.id.help:
                startActivity(new Intent(this, HelpActivity.class));
        }
        return true;
    }

    @Override
    public void onClick(View view) {
        Object tag = view.getTag();
        if (tag.equals(FAB_ACTION.REQUEST_PERMISSION)) {
            try {
                @SuppressLint("InlinedApi")
                Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_WRITE_SETTINGS);
                intent.setData(Uri.parse("package:" + this.getPackageName()));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            } catch (ActivityNotFoundException e) {
                showMessage(R.string.device_not_supported);
            }
        } else if (tag.equals(FAB_ACTION.PERFORM_LOCK)) {
            final boolean lock = isOrientationLocked();
            final int rotation = getWindowManager().getDefaultDisplay().getRotation();
            Settings.System.putInt(mContentResolver, Settings.System.USER_ROTATION, lock ? ROTATION_0 : rotation);
            Settings.System.putInt(mContentResolver, Settings.System.ACCELEROMETER_ROTATION, lock ? 1 : 0);
        }
    }

    @Override
    public boolean onLongClick(View v) {
        return true;
    }


    @Override
    public void onSettingChange(Context context) {
        boolean lock = isOrientationLocked();
        showMessage(lock);
    }

    private <V extends View> V bindView(@IdRes int id) {
        return (V) findViewById(id);
    }

    private Spanned fromHtml(String resource) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return Html.fromHtml(resource, 0);
        } else {
            return Html.fromHtml(resource);
        }
    }

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

    private void showMessage(final boolean lock) {
        mFab.hide();
        mQuickMsg.setText(lock ? R.string.rotation_locked_long : R.string.rotation_unlocked_long);
        mQuickMsg.setVisibility(View.VISIBLE);
        Animation appear = AnimationUtils.loadAnimation(this, R.anim.fade_in);
        appear.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {

            }

            @Override
            public void onAnimationEnd(Animation animation) {
                Animation disappear = AnimationUtils.loadAnimation(MainActivity.this, R.anim.fade_out);
                disappear.setAnimationListener(new Animation.AnimationListener() {
                    @Override
                    public void onAnimationStart(Animation animation) {

                    }

                    @Override
                    public void onAnimationEnd(Animation animation) {
                        mQuickMsg.setVisibility(View.GONE);
                        if (lock) {
                            mFab.setBackgroundTintList(mFabColorLocked);
                            mFab.setImageResource(R.drawable.ic_screen_lock_rotation);
                        } else {
                            mFab.setBackgroundTintList(mFabColorUnlocked);
                            mFab.setImageResource(R.drawable.ic_screen_rotation_undefined);
                        }
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

    private boolean isFirstInstall() {
        try {
            PackageInfo info      =   getPackageManager().getPackageInfo(getPackageName(), 0);
            long firstInstallTime =   info.firstInstallTime;
            long lastUpdateTime   =   info.lastUpdateTime;
            return firstInstallTime == lastUpdateTime;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            return false;
        }
    }

    private void showStartupDialog() {
        final SharedPreferences preferences = getSharedPreferences("prefs", MODE_PRIVATE);
        int lastInstalledVersion = preferences.getInt("vc", 0);
        if (lastInstalledVersion <= BuildConfig.VERSION_CODE - 1) {
            boolean temp = isFirstInstall();
            AlertDialog.Builder dialog = new AlertDialog.Builder(this);
            dialog.setMessage(temp ? R.string.thanks_first : R.string.thanks_old);
            dialog.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    preferences.edit().putInt("vc", BuildConfig.VERSION_CODE).apply();
                }
            });
            dialog.show();
        }
    }
}
