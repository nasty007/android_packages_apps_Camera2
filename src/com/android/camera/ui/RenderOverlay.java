/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.camera.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import com.android.camera.PreviewGestures;
import com.android.camera.debug.Log;

import java.util.ArrayList;
import java.util.List;

public class RenderOverlay extends FrameLayout {

    private static final Log.Tag TAG = new Log.Tag("RenderOverlay");
    private PreviewGestures.SingleTapListener mTapListener;

    interface Renderer {

        public boolean handlesTouch();
        public boolean onTouchEvent(MotionEvent evt);
        public void setOverlay(RenderOverlay overlay);
        public void layout(int left, int top, int right, int bottom);
        public void draw(Canvas canvas);

    }

    private final RenderView mRenderView;
    private final List<Renderer> mClients;
    private PreviewGestures mGestures;
    // reverse list of touch clients
    private final List<Renderer> mTouchClients;
    private final int[] mPosition = new int[2];

    public RenderOverlay(Context context, AttributeSet attrs) {
        super(context, attrs);
        mRenderView = new RenderView(context);
        addView(mRenderView, new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT));
        mClients = new ArrayList<Renderer>(10);
        mTouchClients = new ArrayList<Renderer>(10);
        setWillNotDraw(false);
    }

    public void setGestures(PreviewGestures gestures) {
        mGestures = gestures;
    }

    public void addRenderer(Renderer renderer) {
        mClients.add(renderer);
        renderer.setOverlay(this);
        if (renderer.handlesTouch()) {
            mTouchClients.add(0, renderer);
        }
        renderer.layout(getLeft(), getTop(), getRight(), getBottom());
    }

    public void addRenderer(int pos, Renderer renderer) {
        mClients.add(pos, renderer);
        renderer.setOverlay(this);
        renderer.layout(getLeft(), getTop(), getRight(), getBottom());
    }

    public void remove(Renderer renderer) {
        mClients.remove(renderer);
        renderer.setOverlay(null);
    }

    public int getClientSize() {
        return mClients.size();
    }

    // TODO: Remove this when refactor is done. This is only here temporarily
    // to keep pie working before it's replaced with bottom bar.
    public void directTouchEventsToPie(MotionEvent ev) {
        PieRenderer pie = null;
        for (int i = 0; i < mClients.size(); i++) {
            if (mClients.get(i) instanceof PieRenderer) {
                pie = (PieRenderer) mClients.get(i);
                break;
            }
        }
        if (pie!= null && pie.isOpen()) {
            pie.onTouchEvent(ev);
        } else {
            if (mTapListener != null && ev.getActionMasked() == MotionEvent.ACTION_DOWN) {
                mTapListener.onSingleTapUp(null, ((int) ev.getX()), ((int) ev.getY()));
            }
        }
    }

    // TODO: Remove this when refactor is done. This is only here temporarily
    // to keep pie working before it's replaced with bottom bar.
    public void clear() {
        mGestures = null;
        while (mClients.size() > 0) {
            remove(mClients.get(0));
        }
        mTouchClients.clear();
        mTapListener = null;
    }

    // TODO: Design a touch flow for each module to handle the leftover touch
    // events from the app
    public void setTapListener(PreviewGestures.SingleTapListener listener) {
        mTapListener = listener;
    }


    // Only Gcam is using this right now, which is only using the pie
    // menu for the capture progress.
    // TODO: migrate all modes to PreviewOverlay.
    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (mTapListener != null && ev.getActionMasked() == MotionEvent.ACTION_UP) {
            mTapListener.onSingleTapUp(null, ((int) ev.getX()), ((int) ev.getY()));
        }
        return true;
    }

    public boolean directDispatchTouch(MotionEvent m, Renderer target) {
        mRenderView.setTouchTarget(target);
        boolean res = mRenderView.dispatchTouchEvent(m);
        mRenderView.setTouchTarget(null);
        return res;
    }

    private void adjustPosition() {
        getLocationInWindow(mPosition);
    }

    public int getWindowPositionX() {
        return mPosition[0];
    }

    public int getWindowPositionY() {
        return mPosition[1];
    }

    public void update() {
        mRenderView.invalidate();
    }

    private class RenderView extends View {

        private Renderer mTouchTarget;

        public RenderView(Context context) {
            super(context);
            setWillNotDraw(false);
        }

        public void setTouchTarget(Renderer target) {
            mTouchTarget = target;
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent evt) {
            if (mGestures == null) {
                return false;
            }

            if (mTouchTarget != null) {
                return mTouchTarget.onTouchEvent(evt);
            }
            if (mTouchClients != null) {
                boolean res = false;
                for (Renderer client : mTouchClients) {
                    res |= client.onTouchEvent(evt);
                }
                return res;
            }
            return false;
        }

        @Override
        public void onLayout(boolean changed, int left, int top, int right, int bottom) {
            adjustPosition();
            super.onLayout(changed, left,  top, right, bottom);
            if (mClients == null) return;
            for (Renderer renderer : mClients) {
                renderer.layout(left, top, right, bottom);
            }
        }

        @Override
        public void draw(Canvas canvas) {
            super.draw(canvas);
            if (mClients == null) return;
            boolean redraw = false;
            for (Renderer renderer : mClients) {
                renderer.draw(canvas);
                redraw = redraw || ((OverlayRenderer) renderer).isVisible();
            }
            if (redraw) {
                invalidate();
            }
        }
    }

}
