package yuh.yuh.rotationlock;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.support.annotation.StringRes;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;


@TargetApi(Build.VERSION_CODES.N)
public class QuickSettingsService extends TileService implements SettingsContentObserver.OnSettingsChangeListener {

    @Deprecated
    private ContentResolver mContentResolver;
    private SettingsContentObserver mSettingsContentObserver;

    @Override
    public void onCreate() {
        super.onCreate();
//        if (mSettingsContentObserver == null) {
//            mSettingsContentObserver = new SettingsContentObserver(this);
//            getContentResolver().registerContentObserver(Settings.System.CONTENT_URI, true, mSettingsContentObserver);
//            mSettingsContentObserver.setOnSettingsChangeListener(this);
//        }
    }

    @Override
    public void onSettingChange(Context context) {
        if (Settings.System.canWrite(this)) {
            boolean locked = isOrientationLocked();
            updateTile(!locked);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
//        requestListeningState(this, new ComponentName("com.android.settings", ""));
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public IBinder onBind(Intent intent) {
        IBinder iBinder = super.onBind(intent);
        if (mSettingsContentObserver == null) {
            mSettingsContentObserver = new SettingsContentObserver(this);
            getContentResolver().registerContentObserver(Settings.System.CONTENT_URI, true, mSettingsContentObserver);
            mSettingsContentObserver.setOnSettingsChangeListener(this);
        }
        boolean locked = isOrientationLocked();
        updateTile(!locked);
        if (!Settings.System.canWrite(this)) {
            getQsTile().setState(Tile.STATE_INACTIVE);
        }
        return iBinder;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onTileAdded() {
        super.onTileAdded();
        if (mSettingsContentObserver == null) {
            mSettingsContentObserver = new SettingsContentObserver(this);
            getContentResolver().registerContentObserver(Settings.System.CONTENT_URI, true, mSettingsContentObserver);
            mSettingsContentObserver.setOnSettingsChangeListener(this);
        }
        boolean locked = isOrientationLocked();
        updateTile(!locked);
        if (!Settings.System.canWrite(this)) {
            getQsTile().setState(Tile.STATE_INACTIVE);
        }
    }

    @Override
    public void onTileRemoved() {
        super.onTileRemoved();
        if (mSettingsContentObserver != null) {
            try {
                getContentResolver().unregisterContentObserver(mSettingsContentObserver);
            } catch (NullPointerException ignored) {

            }
        }
    }

    @Override
    public boolean onUnbind(Intent intent) {
        boolean b = super.onUnbind(intent);
        if (mSettingsContentObserver != null) {
            try {
                getContentResolver().unregisterContentObserver(mSettingsContentObserver);
            } catch (NullPointerException ignored) {

            }
        }
        return b;
    }

    @Override
    public void onStartListening() {
        super.onStartListening();
    }

    @Override
    public void onStopListening() {
        super.onStopListening();
    }

    @Override
    public void onClick() {
        int lastInstalledVersion = getSharedPreferences(MainActivity.PREF_FILE_NAME, MODE_PRIVATE).getInt("vc", 0);
        if (lastInstalledVersion < BuildConfig.VERSION_CODE) {
            getSharedPreferences(MainActivity.PREF_FILE_NAME, MODE_PRIVATE).edit().putInt("vc", BuildConfig.VERSION_CODE).apply();
            AlertDialog.Builder dialog = new AlertDialog.Builder(this, R.style.DialogTheme)
                    .setTitle(getString(R.string.nougat_qs_tile_warning_title))
                    .setMessage(getString(R.string.nougat_qs_tile_warning_message))
                    .setPositiveButton(getString(R.string.okay), null);
            showDialog(dialog.create());
            return;
        }
        super.onClick();
        if (isLocked()) {
            showToast(R.string.please_unlock_device);
            return;
        }
        if (Settings.System.canWrite(this)) {
            boolean locked = isOrientationLocked();
            updateTile(locked);
            putSettings(locked);
        } else {
            Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_WRITE_SETTINGS);
            intent.setData(Uri.parse("package:" + this.getPackageName()));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        }
    }


    private void updateTile(boolean locked) {
        Tile tile = getQsTile();
        tile.setLabel(locked ? getString(R.string.rotation_unlocked) : getString(R.string.rotation_locked));
        tile.setIcon(Icon.createWithResource(getApplicationContext(),
                locked ? R.drawable.ic_screen_rotation_undefined_inactive : R.drawable.ic_screen_lock_rotation));
        tile.setState(Tile.STATE_ACTIVE);
//        tile.setState(locked ? Tile.STATE_INACTIVE : Tile.STATE_ACTIVE);

        // Need to call updateTile for the tile to pick up changes.
        tile.updateTile();
    }

    private void putSettings(boolean locked) {
        ContentResolver resolver = getContentResolver();
        if (locked) {
            Settings.System.putInt(resolver, Settings.System.ACCELEROMETER_ROTATION, 1);
            Settings.System.putInt(resolver, Settings.System.USER_ROTATION, Surface.ROTATION_0);
        } else {
            Settings.System.putInt(resolver, Settings.System.ACCELEROMETER_ROTATION, 0);
            Settings.System.putInt(resolver, Settings.System.USER_ROTATION, getWindowManager().getDefaultDisplay().getRotation());
        }
    }

    private boolean isOrientationLocked() {
        switch (Settings.System.getInt(getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, -1)) {
            case 0:
                return true;
            case 1:
                return false;
            default:
                throw new IllegalStateException("Orientation cannot be got");
        }
    }

    private WindowManager getWindowManager() {
        // Use Context#getSystemService
        return this.getSystemService(WindowManager.class);
    }

    private void showToast(CharSequence message) {
        Toast toast = new Toast(this);
        View view = LayoutInflater.from(this).inflate(R.layout.transient_notification, null);
        TextView text = view.findViewById(R.id.message);
        text.setText(message);
        toast.setView(view);
        toast.setDuration(Toast.LENGTH_LONG);
        toast.show();
    }

    private void showToast(@StringRes int message) {
        showToast(getString(message));
    }
}
