package yuh.yuh.rotationlock;

import android.annotation.TargetApi;
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
import android.view.Surface;
import android.view.WindowManager;


@TargetApi(Build.VERSION_CODES.N)
public class QuickSettingsService extends TileService implements SettingsContentObserver.OnSettingsChangeListener {

    private ContentResolver mContentResolver;
    private SettingsContentObserver mSettingsContentObserver;

    @Override
    public void onCreate() {
        super.onCreate();
        mContentResolver = getContentResolver();
//        mSettingsContentObserver = new SettingsContentObserver(this);
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
        return super.onBind(intent);
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
        }
        mSettingsContentObserver.setOnSettingsChangeListener(this);
        mContentResolver.registerContentObserver(Settings.System.CONTENT_URI, true, mSettingsContentObserver);
        boolean locked = isOrientationLocked();
        updateTile(!locked);
    }

    @Override
    public void onTileRemoved() {
        super.onTileRemoved();
        mContentResolver.unregisterContentObserver(mSettingsContentObserver);
        if (mSettingsContentObserver != null) {
            mSettingsContentObserver = null;
        }
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
        super.onClick();
        if (isLocked()) {
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
        tile.setState(locked ? Tile.STATE_INACTIVE : Tile.STATE_ACTIVE);

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
}
