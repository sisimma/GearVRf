/* Copyright 2016 Samsung Electronics Co., LTD
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gearvrf;

import android.graphics.Bitmap;
import android.opengl.GLES20;
import android.view.KeyEvent;
import android.view.MotionEvent;

import org.gearvrf.animation.GVRAnimation;
import org.gearvrf.animation.GVROnFinish;
import org.gearvrf.animation.GVROpacityAnimation;
import org.gearvrf.asynchronous.GVRAsynchronousResourceLoader;
import org.gearvrf.io.GVRInputManager;
import org.gearvrf.script.GVRScriptManager;
import org.gearvrf.utility.ImageUtils;
import org.gearvrf.utility.Log;
import org.gearvrf.utility.Threads;
import org.gearvrf.utility.VrAppSettings;
import org.gearvrf.utility.VrAppSettings.EyeBufferParams.DepthFormat;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.locks.ReentrantLock;

abstract class GVRViewManager extends GVRContext {
    public enum EYE{ LEFT, RIGHT, MULTIVIEW, CENTER};
    GVRViewManager(GVRActivity activity, GVRMain main, boolean useMultiview) {
        super(activity);

        mActivity = activity;
        mMain = main;
        mUseMultiview = useMultiview;
        VrAppSettings vrAppSettings = activity.getAppSettings();
        GVRPerspectiveCamera.setDefaultFovY(vrAppSettings.getEyeBufferParams().getFovY());

        // Clear singletons and per-run data structures
        resetOnRestart();

        GVRAsynchronousResourceLoader.setup(this);
        VrAppSettings appSettings = activity.getAppSettings();
        mScriptManager = new GVRScriptManager(this);
        mEventManager = new GVREventManager(this);
        mInputManager = new GVRInputManagerImpl(this, appSettings.useGazeCursorController(),
                appSettings.useAndroidWearTouchpad());
    }

    void onPause() {}

    void onResume() {}

    void onDestroy() {
        mInputManager.close();
        mScriptManager.destroy();

        mFrameListeners.clear();
        mRunnables.clear();
        mRunnablesPostRender.clear();
        super.onDestroy();
    }

    public GVREventManager getEventManager() {
        return mEventManager;
    }

    public boolean dispatchKeyEvent(KeyEvent event) {
        return mInputManager.dispatchKeyEvent(event);
    }

    public boolean dispatchMotionEvent(MotionEvent event) {
        return mInputManager.dispatchMotionEvent(event);
    }

    @Override
    public GVRScene getMainScene() {
        mMainSceneLock.lock();
        try {
            if (mState == ViewManagerState.SHOWING_SPLASH) {
                return mPendingMainScene;
            } else {
                return mMainScene;
            }
        } finally {
            mMainSceneLock.unlock();
        }
    }

    @Override
    public void setMainScene(GVRScene scene) {
        if (null == scene) {
            throw new IllegalArgumentException();
        }
        mMainSceneLock.lock();
        try {
            if (mState == ViewManagerState.SHOWING_SPLASH) {
                mPendingMainScene = scene;
            } else {
                setMainSceneImpl(scene);
            }
        } finally {
            mMainSceneLock.unlock();
        }
    }

    private void setMainSceneImpl(GVRScene scene) {
        mMainScene = scene;
        NativeScene.setMainScene(scene.getNative());
        getActivity().setCameraRig(scene.getMainCameraRig());
        mInputManager.scanControllers();
        mInputManager.setScene(scene);
    }

    protected boolean updateSensoredScene() {
        if (mSensoredScene != null && mMainScene.equals(mSensoredScene)) {
            return true;
        }

        final GVRCameraRig cameraRig = mMainScene.getMainCameraRig();

        if (null != cameraRig && (mSensoredScene == null || !mMainScene.equals(mSensoredScene))) {
            cameraRig.resetYaw();
            mSensoredScene = mMainScene;
            return true;
        }
        return false;
    }

    public final GVRInputManager getInputManager() {
        return mInputManager;
    }

    final void closeSplashScreen() {
        if (mSplashScreen != null) {
            mSplashScreen.closeSplashScreen();
        }
    }

    @Override
    public void registerDrawFrameListener(GVRDrawFrameListener frameListener) {
        if (!mFrameListeners.contains(frameListener)) {
            mFrameListeners.add(frameListener);
        }
    }

    @Override
    public void unregisterDrawFrameListener(GVRDrawFrameListener frameListener) {
        if (mFrameListeners.contains(frameListener)) {
            mFrameListeners.remove(frameListener);
        }
    }

    /**
     * Called when the surface changed size. When
     * setPreserveEGLContextOnPause(true) is called in the surface, this is
     * called only once.
     */
    void onSurfaceCreated() {
        Log.v(TAG, "onSurfaceCreated");

        Thread currentThread = Thread.currentThread();

        // Reduce contention with other Android processes
        currentThread.setPriority(Thread.MAX_PRIORITY);

        // we know that the current thread is a GL one, so we store it to
        // prevent non-GL thread from calling GL functions
        mGLThreadID = currentThread.getId();

        // Evaluating anisotropic support on GL Thread
        String extensions = GLES20.glGetString(GLES20.GL_EXTENSIONS);
        isAnisotropicSupported = extensions.contains("GL_EXT_texture_filter_anisotropic");

        // Evaluating max anisotropic value if supported
        if (isAnisotropicSupported) {
            maxAnisotropicValue = NativeTextureParameters.getMaxAnisotropicValue();
        }

        mPreviousTimeNanos = GVRTime.getCurrentTime();
        mRenderBundle = makeRenderBundle();
        final DepthFormat depthFormat = getActivity().getAppSettings().getEyeBufferParams().getDepthFormat();
        getActivity().getConfigurationManager().configureRendering(DepthFormat.DEPTH_24_STENCIL_8 == depthFormat);

        final GVRScene scene = null == mMainScene ? new GVRScene(GVRViewManager.this) : mMainScene;
        setMainSceneImpl(scene);
    }

    private void createMainScene() {
        if (getActivity().getAppSettings().showLoadingIcon) {
            mSplashScreen = mMain.createSplashScreen();
            if (mSplashScreen != null) {
                mMainSceneLock.lock();
                try {
                    mState = ViewManagerState.SHOWING_SPLASH;
                    mPendingMainScene = new GVRScene(GVRViewManager.this);
                } finally {
                    mMainSceneLock.unlock();
                }
                mMainScene.addSceneObject(mSplashScreen);
            }
        } else {
            mSplashScreen = null;
        }
    }

    /**
     * Called on surface creation to create a properly configured render bundle
     * @return
     */
    protected IRenderBundle makeRenderBundle() {
        final VrAppSettings.EyeBufferParams eyeBufferParams = getActivity().getAppSettings().getEyeBufferParams();
        return new GVRRenderBundle(this, eyeBufferParams.getResolutionWidth(), eyeBufferParams.getResolutionHeight());
    }

    /**
     * This is the code that needs to be executed before either eye is drawn.
     *
     * @return Current time, from {@link GVRTime#getCurrentTime()}
     */
    private long doMemoryManagementAndPerFrameCallbacks() {
        long currentTime = GVRTime.getCurrentTime();
        mFrameTime = (currentTime - mPreviousTimeNanos) / 1e9f;
        mPreviousTimeNanos = currentTime;

        /*
         * Without the sensor data, can't draw a scene properly.
         */
        if (!(mSensoredScene == null || !mMainScene.equals(mSensoredScene))) {
            Runnable runnable;
            while ((runnable = mRunnables.poll()) != null) {
                try {
                    runnable.run();
                } catch (final Exception exc) {
                    Log.e(TAG, "Runnable-on-GL %s threw %s", runnable, exc.toString());
                    exc.printStackTrace();
                }
            }

            final List<GVRDrawFrameListener> frameListeners = mFrameListeners;
            for (GVRDrawFrameListener listener : frameListeners) {
                try {
                    listener.onDrawFrame(mFrameTime);
                } catch (final Exception exc) {
                    Log.e(TAG, "DrawFrameListener %s threw %s", listener, exc.toString());
                    exc.printStackTrace();
                }
            }
        }

        return currentTime;
    }

    @Override
    public float getFrameTime() {
        return mFrameTime;
    }

    /*
     * Splash screen life cycle
     */

    /**
     * Efficient handling of the state machine.
     *
     * We want to be able to show an animated splash screen after
     * {@link GVRMain#onInit(GVRContext) onInit().} That means our frame
     * handler acts differently on the very first frame than it does during
     * splash screen animations, and differently again when we get to normal
     * mode. If we used a state enum and a switch statement, we'd have to keep
     * the two in synch, and we'd be spending render microseconds in a switch
     * statement, vectoring to a call to a handler. Using a interface
     * implementation instead of a state enum, we just call the handler
     * directly.
     */
    private static class FrameHandler {
        void beforeDrawEyes() {}
        void afterDrawEyes() {}
    }

    private ViewManagerState mState = ViewManagerState.RUNNING;
    private enum ViewManagerState {
        RUNNING,
        SHOWING_SPLASH
    }
    private final ReentrantLock mMainSceneLock = new ReentrantLock();

    private FrameHandler firstFrame = new FrameHandler() {
        @Override
        public void beforeDrawEyes() {
            mMain.setViewManager(GVRViewManager.this);

            createMainScene();

            // execute pending runnables now so any necessary gl calls
            // are done before onInit().  As an example the request to
            // get the GL_MAX_TEXTURE_SIZE needs to be fulfilled.
            synchronized (mRunnables) {
                Runnable runnable = null;
                while ((runnable = mRunnables.poll()) != null) {
                    try {
                        runnable.run();
                    } catch (final Exception exc) {
                        Log.e(TAG, "Runnable-on-GL %s threw %s", runnable, exc.toString());
                        exc.printStackTrace();
                    }
                }
            }

            runOnTheFrameworkThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        getEventManager().sendEvent(mMain, IScriptEvents.class, "onEarlyInit", GVRViewManager.this);
                        getEventManager().sendEvent(mMain, IScriptEvents.class, "onInit", GVRViewManager.this);

                        if (null != mSplashScreen && GVRMain.SplashMode.AUTOMATIC == mMain
                                .getSplashMode() && mMain.getSplashDisplayTime() < 0f) {
                            runOnGlThread(new Runnable() {
                                public void run() {
                                    mSplashScreen.closeSplashScreen();
                                }
                            });
                        }
                    } catch (Throwable t) {
                        t.printStackTrace();
                        runOnGlThread(new Runnable() {
                            public void run() {
                                getActivity().finish();

                                // Just to be safe ...
                                mFrameHandler = splashFrames;
                                firstFrame = null;
                            }
                        });
                    }

                    // Trigger event "onAfterInit" for post-processing of scene
                    // graph after initialization.
                    getEventManager().sendEvent(mMain, IScriptEvents.class,
                            "onAfterInit");
                }
            });

            if (mSplashScreen == null) {
                // No splash screen, notify main scene now.
                notifyMainSceneReady();

                mFrameHandler = normalFrames;
                firstFrame = splashFrames = null;
            } else {
                mFrameHandler = splashFrames;
                firstFrame = null;
            }
        }
    };

    private FrameHandler splashFrames = new FrameHandler() {
        boolean closing;
        @Override
        public void beforeDrawEyes() {
            // splash screen post-init animations
            long currentTime = doMemoryManagementAndPerFrameCallbacks();

            if (closing) {
                return;
            }

            final boolean timeoutExpired = (null == mSplashScreen
                    || (0 <= mMain.getSplashDisplayTime() && currentTime >= mSplashScreen.mTimeout));
            if (mSplashScreen != null && (timeoutExpired || mSplashScreen.closeRequested())) {
                if (mSplashScreen.closeRequested() || mMain.getSplashMode() == GVRMain.SplashMode.AUTOMATIC) {

                    closing = true;
                    Threads.spawnLow(new Runnable() {
                        @Override
                        public void run() {
                            runOnGlThread(new Runnable() {
                                public void run() {
                                    new GVROpacityAnimation(mSplashScreen, mMain.getSplashFadeTime(), 0) //
                                            .setOnFinish(new GVROnFinish() {

                                                @Override
                                                public void finished(GVRAnimation animation) {
                                                    mMainSceneLock.lock();
                                                    try {
                                                        mState = ViewManagerState.RUNNING;
                                                        setMainScene(mPendingMainScene);
                                                        mPendingMainScene = null;
                                                    } finally {
                                                        mMainSceneLock.unlock();
                                                    }
                                                    // Splash screen finishes. Notify main
                                                    // scene it is ready.
                                                    GVRViewManager.this.notifyMainSceneReady();
                                                    mSplashScreen = null;

                                                    mFrameHandler = normalFrames;
                                                    splashFrames = null;
                                                }
                                            }) //
                                            .start(getAnimationEngine());

                                    mSplashScreen = null;
                                }
                            });
                        }
                    });
                }
            }
        }
    };

    private final FrameHandler normalFrames = new FrameHandler() {

        public void beforeDrawEyes() {
            mMainScene.resetStats();
            doMemoryManagementAndPerFrameCallbacks();

            runOnTheFrameworkThread(new Runnable() {
                public void run() {
                    try {
                        mMain.onStep();
                    } catch (final Exception exc) {
                        Log.e(TAG, "Exception from onStep: %s", exc.toString());
                        exc.printStackTrace();
                    }
                }
            });
        }

        @Override
        public void afterDrawEyes() {
            mMainScene.updateStats();
        }
    };


    // Send onInit and onAfterInit events to main scene when it is ready.
    // When there is a splash screen, it is called after the splash screen has
    // completed.
    // If there is no splash screen, it is called after GVRMain.onInit()
    // returns.
    private void notifyMainSceneReady() {
        runOnTheFrameworkThread(new Runnable() {
            @Override
            public void run() {
                // Initialize the main scene
                getEventManager().sendEvent(mMainScene, ISceneEvents.class, "onInit", GVRViewManager.this, mMainScene);

                // Late-initialize the main scene
                getEventManager().sendEvent(mMainScene, ISceneEvents.class, "onAfterInit");
            }
        });
    }

    @Override
    public void runOnGlThread(Runnable runnable) {
        if (mGLThreadID == Thread.currentThread().getId()) {
            runnable.run();
        } else {
            mRunnables.add(runnable);
        }
    }

    @Override
    public void runOnGlThreadPostRender(int delayFrames, Runnable runnable) {
        synchronized (mRunnablesPostRender) {
            mRunnablesPostRender.put(runnable, delayFrames);
        }
    }

    protected void beforeDrawEyes() {
        GVRNotifications.notifyBeforeStep();
        mFrameHandler.beforeDrawEyes();

        GVRPerspectiveCamera centerCamera = mMainScene.getMainCameraRig().getCenterCamera();
        makeShadowMaps(mMainScene.getNative(), mRenderBundle.getMaterialShaderManager().getNative(),
                mRenderBundle.getPostEffectRenderTextureA().getWidth(),
                mRenderBundle.getPostEffectRenderTextureA().getHeight());
        cull(mMainScene.getNative(), centerCamera.getNative(), mRenderBundle.getMaterialShaderManager().getNative());
    }

    protected void afterDrawEyes() {
        // Execute post-rendering tasks (after drawing eyes, but
        // before afterDrawEyes handlers)
        synchronized (mRunnablesPostRender) {
            for (Iterator<Map.Entry<Runnable, Integer>> it = mRunnablesPostRender.entrySet().iterator(); it
                    .hasNext();) {
                Map.Entry<Runnable, Integer> entry = it.next();
                if (entry.getValue() <= 0) {
                    entry.getKey().run();
                    it.remove();
                } else {
                    entry.setValue(entry.getValue() - 1);
                }
            }
        }

        mFrameHandler.afterDrawEyes();
        finalizeUnreachableObjects();
        GVRNotifications.notifyAfterStep();
    }

    protected void renderCamera(GVRScene scene, GVRCamera camera, IRenderBundle
            renderBundle, boolean isMultiviewSet) {

        renderCamera(scene.getNative(), camera.getNative(),
                renderBundle.getMaterialShaderManager().getNative(),
                renderBundle.getPostEffectShaderManager().getNative(),
                renderBundle.getPostEffectRenderTextureA().getNative(),
                renderBundle.getPostEffectRenderTextureB().getNative(), isMultiviewSet);
    }

    void cullAndRender(GVRRenderTarget renderTarget, GVRScene scene)
    {
        cullAndRender(renderTarget.getNative(), scene.getNative(),
                mRenderBundle.getMaterialShaderManager().getNative(),
                mRenderBundle.getPostEffectShaderManager().getNative(),
                mRenderBundle.getPostEffectRenderTextureA().getNative(),
                mRenderBundle.getPostEffectRenderTextureB().getNative());
    }

    @Override
    public GVRScriptManager getScriptManager() {
        return mScriptManager;
    }

    @Override
    public GVRMaterialShaderManager getMaterialShaderManager() {
        return mRenderBundle.getMaterialShaderManager();
    }

    @Override
    public GVRPostEffectShaderManager getPostEffectShaderManager() {
        return mRenderBundle.getPostEffectShaderManager();
    }

    protected GVRScreenshotCallback mScreenshotCenterCallback;
    protected GVRScreenshotCallback mScreenshotLeftCallback;
    protected GVRScreenshotCallback mScreenshotRightCallback;
    protected GVRScreenshot3DCallback mScreenshot3DCallback;

    @Override
    public void captureScreenCenter(GVRScreenshotCallback callback) {
        if (callback == null) {
            throw new IllegalArgumentException("callback should not be null.");
        } else {
            mScreenshotCenterCallback = callback;
        }
    }

    @Override
    public void captureScreenLeft(GVRScreenshotCallback callback) {
        if (callback == null) {
            throw new IllegalArgumentException("callback should not be null.");
        } else {
            mScreenshotLeftCallback = callback;
        }
    }

    @Override
    public void captureScreenRight(GVRScreenshotCallback callback) {
        if (callback == null) {
            throw new IllegalArgumentException("callback should not be null.");
        } else {
            mScreenshotRightCallback = callback;
        }
    }

    @Override
    public void captureScreen3D(GVRScreenshot3DCallback callback) {
        mScreenshot3DCallback = callback;
    }

    protected void readRenderResult(int texId, GVRViewManager.EYE eye, boolean isMultiviewSet) {
        if (mReadbackBuffer == null) {
            final VrAppSettings settings = mActivity.getAppSettings();
            final VrAppSettings.EyeBufferParams eyeBufferParams = settings.getEyeBufferParams();
            mReadbackBufferWidth = eyeBufferParams.getResolutionWidth();
            mReadbackBufferHeight = eyeBufferParams.getResolutionHeight();

            mReadbackBuffer = ByteBuffer.allocateDirect(mReadbackBufferWidth * mReadbackBufferHeight * 4);
            mReadbackBuffer.order(ByteOrder.nativeOrder());
        }
        readRenderResultNative(mReadbackBuffer, isMultiviewSet ? texId : 0 , eye.ordinal(), isMultiviewSet);
    }

    protected void returnScreenshotToCaller(final GVRScreenshotCallback callback, final int width, final int height) {
        // run the callback function in a background thread
        final byte[] byteArray = Arrays.copyOf(mReadbackBuffer.array(), mReadbackBuffer.array().length);
        Threads.spawn(new Runnable() {
            public void run() {
                final Bitmap capturedBitmap = ImageUtils.generateBitmapFlipV(byteArray, width, height);
                callback.onScreenCaptured(capturedBitmap);
            }
        });
    }

    protected void returnScreenshot3DToCaller(final GVRScreenshot3DCallback callback, final Bitmap[] bitmaps,
                                              final int width, final int height) {

        // run the callback function in a background thread
        Threads.spawn(new Runnable() {
            public void run() {
                final Bitmap[] bitmapArray = new Bitmap[6];
                Runnable[] threads = new Runnable[6];

                for (int i = 0; i < 6; i++) {
                    final int index = i;
                    threads[i] = new Runnable() {
                        public void run() {
                            final Bitmap bitmap = bitmaps[index];
                            bitmaps[index] = null;

                            synchronized (this) {
                                bitmapArray[index] = bitmap;
                                notify();
                            }
                        }
                    };
                }

                for (Runnable thread : threads) {
                    Threads.spawnLow(thread);
                }

                for (int i = 0; i < 6; i++) {
                    synchronized (threads[i]) {
                        if (bitmapArray[i] == null) {
                            try {
                                threads[i].wait();
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }

                callback.onScreenCaptured(bitmapArray);
            }
        });
    }

    // capture 3D screenshot
    protected void capture3DScreenShot() {
        if (mScreenshot3DCallback == null) {
            return;
        }
        final Bitmap[] bitmaps = new Bitmap[6];
        renderSixCamerasAndReadback(mMainScene.getMainCameraRig(), bitmaps, mUseMultiview);
        returnScreenshot3DToCaller(mScreenshot3DCallback, bitmaps, mReadbackBufferWidth, mReadbackBufferHeight);

        mScreenshot3DCallback = null;
    }

    protected void captureRightEye(int texId) {
        captureEye(mScreenshotRightCallback, texId, EYE.RIGHT);
        mScreenshotRightCallback = null;
    }

    protected void captureLeftEye(int texId) {
        captureEye(mScreenshotLeftCallback, texId, EYE.LEFT);
        mScreenshotLeftCallback = null;
    }

        // capture screenshot of an eye
    private void captureEye(GVRScreenshotCallback callback, int texId, GVRViewManager.EYE eye) {
        if (null == callback) {
            return;
        }

        readRenderResult(texId, eye, mUseMultiview);
        returnScreenshotToCaller(callback, mReadbackBufferWidth, mReadbackBufferHeight);
    }

    // capture center eye
    protected void captureCenterEye() {
        if (mScreenshotCenterCallback == null) {
            return;
        }

        GVRRenderTexture eyeCaptureRenderTexture = null;

        // if the multiview is set, render on normal FBO instead of multiview buffers
        if(mUseMultiview) {
            eyeCaptureRenderTexture = mRenderBundle.getEyeCaptureRenderTexture();
            eyeCaptureRenderTexture.beginRendering();
        }
        final GVRCamera centerCamera = mMainScene.getMainCameraRig().getCenterCamera();
        final GVRPostEffect postEffect = new GVRPostEffect(this, GVRPostEffect.GVRPostEffectShaderType.HorizontalFlip.ID);
        centerCamera.addPostEffect(postEffect);
        renderCamera(mMainScene, centerCamera, mRenderBundle, false);
        centerCamera.removePostEffect(postEffect);
        if(mUseMultiview)
            eyeCaptureRenderTexture.endRendering();

        readRenderResult(mUseMultiview ? eyeCaptureRenderTexture.getId() : 0, EYE.CENTER, false);

        final Bitmap bitmap = Bitmap.createBitmap(mReadbackBufferWidth, mReadbackBufferHeight, Bitmap.Config.ARGB_8888);
        mReadbackBuffer.rewind();
        bitmap.copyPixelsFromBuffer(mReadbackBuffer);

        final GVRScreenshotCallback callback = mScreenshotCenterCallback;
        Threads.spawn(new Runnable() {
            public void run() {
                callback.onScreenCaptured(bitmap);
            }
        });

        mScreenshotCenterCallback = null;
    }

    private void renderOneCameraAndAddToList(final GVRPerspectiveCamera centerCamera, final Bitmap[] bitmaps, int index, boolean isMultiviewSet) {

        GVRRenderTexture eyeCaptureRenderTexture = null;

        if(isMultiviewSet) {
            eyeCaptureRenderTexture = mRenderBundle.getEyeCaptureRenderTexture();
            eyeCaptureRenderTexture.beginRendering();
        }
        renderCamera(mMainScene, centerCamera, mRenderBundle, false);

        if(isMultiviewSet)
            eyeCaptureRenderTexture.endRendering();

        readRenderResult(0,EYE.CENTER, false);
        bitmaps[index] = Bitmap.createBitmap(mReadbackBufferWidth, mReadbackBufferHeight, Bitmap.Config.ARGB_8888);
        mReadbackBuffer.rewind();
        bitmaps[index].copyPixelsFromBuffer(mReadbackBuffer);
    }

    private void renderSixCamerasAndReadback(final GVRCameraRig mainCameraRig, final Bitmap[] bitmaps, boolean isMultiviewSet) {
        // temporarily create a center camera
        GVRPerspectiveCamera centerCamera = new GVRPerspectiveCamera(this);
        centerCamera.setFovY(90.0f);
        centerCamera.setRenderMask(GVRRenderData.GVRRenderMaskBit.Left | GVRRenderData.GVRRenderMaskBit.Right);
        GVRSceneObject centerCameraObject = new GVRSceneObject(this);

        centerCameraObject.attachCamera(centerCamera);
        centerCamera.addPostEffect(new GVRPostEffect(this, GVRPostEffect.GVRPostEffectShaderType.HorizontalFlip.ID));

        mainCameraRig.getOwnerObject().addChildObject(centerCameraObject);
        GVRTransform centerCameraTransform = centerCameraObject.getTransform();
        int index = 0;
        // render +x face
        centerCameraTransform.rotateByAxis(-90, 0, 1, 0);
        renderOneCameraAndAddToList(centerCamera, bitmaps, index++, isMultiviewSet);
        // render -x face
        centerCameraTransform.rotateByAxis(180, 0, 1, 0);
        renderOneCameraAndAddToList(centerCamera, bitmaps, index++, isMultiviewSet );
        // render +y face
        centerCameraTransform.rotateByAxis(-90, 0, 1, 0);
        centerCameraTransform.rotateByAxis(90, 1, 0, 0);
        renderOneCameraAndAddToList(centerCamera, bitmaps, index++, isMultiviewSet);
        // render -y face
        centerCameraTransform.rotateByAxis(180, 1, 0, 0);
        renderOneCameraAndAddToList(centerCamera, bitmaps, index++, isMultiviewSet);
        // render +z face
        centerCameraTransform.rotateByAxis(90, 1, 0, 0);
        centerCameraTransform.rotateByAxis(180, 0, 1, 0);
        renderOneCameraAndAddToList(centerCamera, bitmaps, index++, isMultiviewSet);
        // render -z face
        centerCameraTransform.rotateByAxis(180, 0, 1, 0);
        renderOneCameraAndAddToList(centerCamera, bitmaps, index++, isMultiviewSet);
        centerCameraObject.detachCamera();
        mainCameraRig.getOwnerObject().removeChildObject(centerCameraObject);
    }

    protected void captureFinish() {
        if (mScreenshotLeftCallback == null && mScreenshotRightCallback == null
                && mScreenshotCenterCallback == null && mScreenshot3DCallback == null) {
            mReadbackBuffer = null;
        }
    }

    private final GVRScriptManager mScriptManager;
    protected final GVRActivity mActivity;
    protected float mFrameTime;
    protected long mPreviousTimeNanos;
    protected boolean mUseMultiview;
    protected FrameHandler mFrameHandler = firstFrame;

    protected List<GVRDrawFrameListener> mFrameListeners = new CopyOnWriteArrayList<GVRDrawFrameListener>();
    protected final Queue<Runnable> mRunnables = new LinkedBlockingQueue<Runnable>();
    protected final Map<Runnable, Integer> mRunnablesPostRender = new HashMap<Runnable, Integer>();

    protected GVRScene mMainScene;
    protected GVRScene mPendingMainScene;
    protected GVRScene mSensoredScene;

    protected SplashScreen mSplashScreen;

    private final GVREventManager mEventManager;
    protected final GVRInputManagerImpl mInputManager;
    protected IRenderBundle mRenderBundle;

    protected GVRMain mMain;

    protected ByteBuffer mReadbackBuffer;
    protected int mReadbackBufferWidth;
    protected int mReadbackBufferHeight;


    protected native void renderCamera(long scene, long camera, long shaderManager,
                                       long postEffectShaderManager, long postEffectRenderTextureA, long postEffectRenderTextureB, boolean isMultiviewSet);
    protected native void cull(long scene, long camera, long shader_manager);
    protected native void makeShadowMaps(long scene, long shader_manager, int width, int height);
    protected native void cullAndRender(long render_target, long scene, long shader_manager,
                                        long postEffectShaderManager, long postEffectRenderTextureA, long postEffectRenderTextureB);
    private native static void readRenderResultNative(Object readbackBuffer, int texId, int eye, boolean isMultiviewSet);

    private static final String TAG = "GVRViewManager";
}
