package yuh.yuh.rotationlock;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.support.annotation.StringRes;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

/**
 * The service that is used for the QS Tile.
 * It targets Api 24, so there's no problem with previous compatibility.
 */
@TargetApi(Build.VERSION_CODES.N)
public class QuickSettingsService extends TileService implements SettingsContentObserver.OnSettingsChangeListener {

    /**
     * @see SettingsContentObserver
     */
    private SettingsContentObserver mSettingsContentObserver;

    @Override
    public void onCreate() {
        super.onCreate();
    }

    /**
     * Invoked everytime the a setting is changed.
     * Since version 3.01(12), it is only invoked when the QS Panel is revealed (user make a pull down from the status bar).
     * @see #onStartListening()
     * @see #onStopListening()
     * @see SettingsContentObserver.OnSettingsChangeListener
     */
    @Override
    public void onSettingChange(Context context) {
        boolean locked = isOrientationLocked();
        updateTile(!locked);
    }

    /**
     * Called when the QS Tile is added to the QS Panel
     */
    @Override
    public void onTileAdded() {
        super.onTileAdded();
    }

    /**
     * Called when the QS Tile enters the listening state.
     */
    @Override
    public void onStartListening() {
        super.onStartListening();
        boolean locked = isOrientationLocked();
        updateTile(!locked);
        if (mSettingsContentObserver == null) {
            mSettingsContentObserver = new SettingsContentObserver(this);
            getContentResolver().registerContentObserver(Settings.System.CONTENT_URI, true, mSettingsContentObserver);
            mSettingsContentObserver.setOnSettingsChangeListener(this);
        }
    }

    /**
     * Called when the QS Tile exits the listening state.
     */
    @Override
    public void onStopListening() {
        super.onStopListening();
        if (mSettingsContentObserver != null ) {
            getContentResolver().unregisterContentObserver(mSettingsContentObserver);
            mSettingsContentObserver = null;
        }
    }

    /**
     * Called when the QS Tile is removed from the QS Panel.
     */
    @Override
    public void onTileRemoved() {
        super.onTileRemoved();
    }

    /**
     * Called when the QS Tile is clicked.
     */
    @Override
    public void onClick() {
        SharedPreferences preferences = getSharedPreferences(MainActivity.PREF_FILE_NAME, MODE_PRIVATE);
        int lastInstalledVersion = preferences.getInt("vc", 0);
        if (lastInstalledVersion < BuildConfig.VERSION_CODE) {
            preferences.edit().putInt("vc", BuildConfig.VERSION_CODE).apply();
//            if (lastInstalledVersion < BuildConfig.VERSION_CODE) {
//                AlertDialog.Builder dialog = new AlertDialog.Builder(this, R.style.DialogTheme)
//                        .setTitle(getString(R.string.nougat_qs_tile_warning_title))
//                        .setMessage(getString(R.string.nougat_qs_tile_warning_message))
//                        .setPositiveButton(getString(R.string.okay), null);
//                showDialog(dialog.create());
//                return;
//            }
        }

        // Super method
        super.onClick();

        // If the screen is locked, show a quick Toast and perform no action.
//        if (isLocked()) {
//            MainActivity.showToast(this, R.string.please_unlock_device);
//            return;
//        }

        // Okay, the screen is not locked, perform (un)lock action based on current status if user has granted the
        // Manifest.WRITE_SETTINGS permission. If not, then request the permission.
        if (Settings.System.canWrite(this)) {
            boolean locked = isOrientationLocked();
            int orientation = getWindowManager().getDefaultDisplay().getRotation();
            putSettings(locked, orientation);
        } else {
            Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_WRITE_SETTINGS);
            intent.setData(Uri.parse("package:" + this.getPackageName()));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        }
    }

    /**
     * Update the Tile.
     * @param locked The current orientation lock status. If it is locked, then do unlock, and vice versa.
     *
     * Since version 3.01(12), the inner orientation instance is got from {@link Settings.System#USER_ROTATION} instead of from device sensor.
     * This should fix the wrong state if user clicks the built-in QS Tile.
     */
    private void updateTile(boolean locked) {
        int orientation = Settings.System.getInt(getContentResolver(), Settings.System.USER_ROTATION, Surface.ROTATION_0);
        updateTile(locked, orientation);
    }

    /**
     * Update the Tile based on defined orientation.
     * @param locked The current orientation lock status. If it is locked, then do unlock, and vice versa.
     * @param orientation The base orientation.
     */
    private void updateTile(boolean locked, int orientation) {
        // Check if the orientation is landscape.
        boolean landscape = orientation == Surface.ROTATION_90 || orientation == Surface.ROTATION_270;

        // Update the Tile
        Tile tile = getQsTile();
        tile.setLabel(locked ? getString(R.string.auto_rotate) : landscape ? getString(R.string.landscape)
                : getString(R.string.portrait));
        tile.setIcon(Icon.createWithResource(this, locked ? R.drawable.ic_screen_rotation_undefined_inactive
                : landscape ? R.drawable.ic_screen_lock_landscape
                : R.drawable.ic_screen_lock_portrait));
        tile.setState(Tile.STATE_ACTIVE);
        tile.updateTile();
    }

    /**
     * Put Settings.
     * @deprecated This method is deprecated.
     * @see #putSettings(boolean, int)
     */
    @Deprecated
    private void putSettings(boolean locked) {
        ContentResolver resolver = getContentResolver();
        Settings.System.putInt(resolver, Settings.System.ACCELEROMETER_ROTATION, locked ? 1 : 0);
        Settings.System.putInt(resolver, Settings.System.USER_ROTATION, locked ? Surface.ROTATION_0 : getWindowManager().getDefaultDisplay().getRotation());
    }

    /**
     * Put Settings.
     * @param locked The orientation lock mode. If the orientation is locked, the do unlock, and vice versa.
     * @param orientation The orientation to be put, which is based on device's sensor.
     */
    private void putSettings(boolean locked, int orientation) {
        ContentResolver resolver = getContentResolver();
        Settings.System.putInt(resolver, Settings.System.ACCELEROMETER_ROTATION, locked ? 1 : 0);
        Settings.System.putInt(resolver, Settings.System.USER_ROTATION, locked ? Surface.ROTATION_0 : orientation);
    }

    /**
     * Check if orientation is locked.
     */
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

    /**
     * Get WindowManager instance using {@link Context#getSystemService(Class)}. Because the class targets API24 so it's free to call that method.
     */
    private WindowManager getWindowManager() {
        return this.getSystemService(WindowManager.class);
    }

    /**
     * Show Toast with message. The Toast is heavily customised.
     */
    private void showToast(CharSequence message) {
        Toast toast = new Toast(this);
        View view = LayoutInflater.from(this).inflate(R.layout.transient_notification, null, false);
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
