package yuh.yuh.rotationlock;

import android.content.Context;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.provider.Settings;
import android.support.annotation.RequiresApi;

class SettingsContentObserver extends ContentObserver {

    private Context mContext;
    private OnSettingsChangeListener mOnSettingsChangeListener;

    SettingsContentObserver(Context context) {
        this(context, null);
    }

    SettingsContentObserver(Context context, Handler handler) {
        super(handler);
        mContext = context;
    }

    @Override
    public boolean deliverSelfNotifications() {
        // Super method
        return super.deliverSelfNotifications();
    }

    @Override
    public void onChange(boolean selfChange, Uri uri) {
        super.onChange(selfChange, uri);
        boolean canWrite = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.System.canWrite(mContext);
        if (canWrite) {
            mOnSettingsChangeListener.onSettingChange(mContext);
        }
    }


    void setOnSettingsChangeListener(OnSettingsChangeListener listener) {
        mOnSettingsChangeListener = listener;
    }

    interface OnSettingsChangeListener {
        void onSettingChange(Context context);
    }
}
