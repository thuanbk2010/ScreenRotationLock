package yuh.yuh.rotationlock;

import android.content.Context;
import android.content.res.Resources;
import android.os.Build;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.support.annotation.StringRes;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

/**
 *
 */
public class QuickMessage extends FrameLayout {

    private TextView mTextView;

    public QuickMessage(Context context) {
        super(context);
    }

    public QuickMessage(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public QuickMessage(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public QuickMessage(Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        Resources resources = getResources();
        this.setBackgroundResource(R.drawable.shadow);
        this.setPadding(10, 5, 10, 10);
        TextView textView = getTextView();
        ViewGroup tvp = (ViewGroup) textView.getParent();
        if (tvp != null) {
            tvp.removeView(textView);
        }
        addView(textView);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
    }

    @Override
    public void setForegroundGravity(int foregroundGravity) {
        super.setForegroundGravity(foregroundGravity);
    }

    private TextView getTextView() {
        if (mTextView == null) {
            mTextView = new TextView(getContext());
            MarginLayoutParams lp = new MarginLayoutParams(-1, -1);
            lp.setMargins(0, 0, 0, 0);
            mTextView.setLayoutParams(lp);
            mTextView.setGravity(Gravity.CENTER_VERTICAL | Gravity.CENTER_HORIZONTAL);
            mTextView.setPadding(40, mTextView.getPaddingTop(), 40, mTextView.getPaddingBottom());
            mTextView.setTextSize(17);
            mTextView.setTextColor(getContext().getResources().getColor(android.R.color.white));
        }
        return mTextView;
    }

    public void setText(CharSequence text) {
        getTextView().setText(text);
    }

    public void setText(@StringRes int text) {
        getTextView().setText(text);
    }
}
