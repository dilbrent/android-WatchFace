/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.wearable.watchface.watchface;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.Formatter;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import com.example.android.wearable.watchface.R;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import android.os.PowerManager;


/**
 * Demonstrates interactive watch face capabilities, i.e., touching the display and registering
 * three different events: touch, touch-cancel and tap. The watch face UI will show the count of
 * these events as they occur. See the {@code onTapCommand} below.
 */
public class InteractiveWatchFaceService extends CanvasWatchFaceService {

    private static final long TICK_PERIOD_MILLIS = 100;
    private Handler timeTick;

    private static final String TAG = "InteractiveWatchFace";

    private static final Typeface BOLD_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);
    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private class Engine extends CanvasWatchFaceService.Engine {

        private Paint mTextPaint;
        private final Paint mPeekCardBackgroundPaint = new Paint();

        private float mTextSize=10; //default to 10
        private float mXOffset;
        private float mYOffset;
        private float mTextSpacingHeight;
        private int mScreenTextColor = Color.GREEN;

        private int mTouchCommandTotal;
        private int mTouchCancelCommandTotal;
        private int mTapCommandTotal;

        private int mTouchCoordinateX;
        private int mTouchCoordinateY;

        protected PowerManager.WakeLock mWakeLock;

        private final Rect mCardBounds = new Rect();

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        private boolean mLowBitAmbient;

        @Override
        public void onCreate(SurfaceHolder holder) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onCreate");
            }
            super.onCreate(holder);

            final PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            this.mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "My Tag");
            this.mWakeLock.acquire();

            timeTick = new Handler(Looper.myLooper());
            startTimerIfNecessary();

            /** Accepts tap events via WatchFaceStyle (setAcceptsTapEvents(true)). */
            setWatchFaceStyle(new WatchFaceStyle.Builder(InteractiveWatchFaceService.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .setAcceptsTapEvents(true)
                    .build());

            Resources resources = InteractiveWatchFaceService.this.getResources();
            mTextSpacingHeight = resources.getDimension(R.dimen.interactive_text_size)+2;

            mTextPaint = new Paint();
            mTextPaint.setColor(mScreenTextColor);
            mTextPaint.setTypeface(BOLD_TYPEFACE);
            mTextPaint.setAntiAlias(true);

            mTouchCommandTotal = 0;
            mTouchCancelCommandTotal = 0;
            mTapCommandTotal = 0;

            mTouchCoordinateX = 0;
            mTouchCoordinateX = 0;
        }


        private void startTimerIfNecessary() {
            timeTick.removeCallbacks(timeRunnable);
            //if (isVisible() && !isInAmbientMode()) {
                timeTick.post(timeRunnable);
            //}
        }

        private final Runnable timeRunnable = new Runnable() {
            @Override
            public void run() {
                onSecondTick();

                if (isVisible() && !isInAmbientMode()) {
                    timeTick.postDelayed(this, TICK_PERIOD_MILLIS);
                }
            }
        };

        private void onSecondTick() {
            invalidate();
        }

        private void invalidateIfNecessary() {
            if (isVisible() && !isInAmbientMode()) {
                invalidate();
            }
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);
            startTimerIfNecessary();
        }


        @Override
        public void onDestroy() {

            this.mWakeLock.release();
            timeTick.removeCallbacks(timeRunnable);
            super.onDestroy();
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onApplyWindowInsets: " + (insets.isRound() ? "round" : "square"));
            }
            super.onApplyWindowInsets(insets);

            /** Loads offsets / text size based on device type (square vs. round). */
            Resources resources = InteractiveWatchFaceService.this.getResources();
            boolean isRound = insets.isRound();
            mXOffset = resources.getDimension(
                    isRound ? R.dimen.interactive_x_offset_round : R.dimen.interactive_x_offset);
            mYOffset = resources.getDimension(
                    isRound ? R.dimen.interactive_y_offset_round : R.dimen.interactive_y_offset);

            mTextSize = resources.getDimension(
                    isRound ? R.dimen.interactive_text_size_round : R.dimen.interactive_text_size);

            mTextPaint.setTextSize(mTextSize);
        }

        @Override
        public void onPeekCardPositionUpdate(Rect bounds) {
            super.onPeekCardPositionUpdate(bounds);
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onPeekCardPositionUpdate: " + bounds);
            }
            super.onPeekCardPositionUpdate(bounds);
            if (!bounds.equals(mCardBounds)) {
                mCardBounds.set(bounds);
                invalidate();
            }
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);

            boolean burnInProtection = properties.getBoolean(PROPERTY_BURN_IN_PROTECTION, false);
            mTextPaint.setTypeface(burnInProtection ? NORMAL_TYPEFACE : BOLD_TYPEFACE);

            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);

            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onPropertiesChanged: burn-in protection = " + burnInProtection
                        + ", low-bit ambient = " + mLowBitAmbient);
            }
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onAmbientModeChanged: " + inAmbientMode);
            }

            if (mLowBitAmbient) {
                boolean antiAlias = !inAmbientMode;
                mTextPaint.setAntiAlias(antiAlias);
            }
            invalidate();
            startTimerIfNecessary();
        }

        /*
         * Captures tap event (and tap type) and increments correct tap type total.
         */
        @Override
        public void onTapCommand(int tapType, int x, int y, long eventTime) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Tap Command: " + tapType);
            }

            mTouchCoordinateX = x;
            mTouchCoordinateY = y;

            switch(tapType) {
                case TAP_TYPE_TOUCH:
                    mTouchCommandTotal++;
                    break;
                case TAP_TYPE_TOUCH_CANCEL:
                    mTouchCancelCommandTotal++;
                    break;
                case TAP_TYPE_TAP:
                    mTapCommandTotal++;
                    break;
            }

            invalidate();
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onTimeTick: ambient = " + isInAmbientMode());
            }
            invalidate();
        }


        @Override
        public void onDraw(Canvas canvas, Rect bounds) {

            WifiManager wifiMan = (WifiManager) getSystemService(Context.WIFI_SERVICE);
            WifiInfo wifiInf = wifiMan.getConnectionInfo();
            int ipAddress = wifiInf.getIpAddress();
            String ap=wifiInf.getSSID();
            String bs=wifiInf.getBSSID();
            String ip = String.format("%d.%d.%d.%d", (ipAddress & 0xff),(ipAddress >> 8 & 0xff),(ipAddress >> 16 & 0xff),(ipAddress >> 24 & 0xff));
            DateFormat df = new SimpleDateFormat("MM'/'dd'/'yy");
            DateFormat tf = new SimpleDateFormat("HH:mm:ss z");
            Date d=Calendar.getInstance().getTime();
            String date = df.format(d);
            String time = tf.format(d);

            int wide=canvas.getWidth();
            int high=canvas.getHeight();

            /** Draws background */
            canvas.drawColor(Color.BLACK);

            mTextPaint.setColor(Color.CYAN);

            canvas.drawText( ip,wide/2-70, mTextSize*2, mTextPaint );
            canvas.drawText( "S:"+ap,wide/2-90, mTextSize*3, mTextPaint );
            canvas.drawText( "B:"+bs,wide/2-90, mTextSize*4, mTextPaint );

            canvas.drawText( time, wide/2-68, high-10-mTextSpacingHeight, mTextPaint );

            canvas.drawText( date,wide/2-50,high-10, mTextPaint );

            mTextPaint.setTextSize(mTextSize/2);
            mTextPaint.setColor(Color.WHITE);

            canvas.drawText( ""+mTouchCoordinateX,5,high/2-10, mTextPaint);
            canvas.drawText( ""+mTouchCoordinateY,5,high/2-10 + mTextSpacingHeight/2, mTextPaint);

            mTextPaint.setTextSize(mTextSize);
            mTextPaint.setColor(mScreenTextColor);

            canvas.drawText(
                    "TAP: " + String.valueOf(mTapCommandTotal),
                    mXOffset,
                    mYOffset,
                    mTextPaint);

            canvas.drawText(
                    "CANCEL: " + String.valueOf(mTouchCancelCommandTotal),
                    mXOffset,
                    mYOffset + mTextSpacingHeight,
                    mTextPaint);

            canvas.drawText(
                    "TOUCH: " + String.valueOf(mTouchCommandTotal),
                    mXOffset,
                    (float) (mYOffset + (mTextSpacingHeight * 2)),
                    mTextPaint);

            /** Covers area under peek card */
            if (isInAmbientMode()) {
                canvas.drawRect(mCardBounds, mPeekCardBackgroundPaint);
            }
        }
    }
}
