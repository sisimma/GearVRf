/* Copyright 2015 Samsung Electronics Co., LTD
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

package org.gearvrf.scene_objects.view;

import org.gearvrf.scene_objects.GVRViewSceneObject;

import android.graphics.Canvas;
import android.view.View;

/**
 * Because it is not possible add a {@linkplain View Android view} to the {@link org.gearvrf.GVRScene},
 * {@link GVRView} is a special view that provides a way to draw {@link View} into some
 * {@link GVRViewSceneObject} added to {@linkplain org.gearvrf.GVRScene the scene graph}. Call
 * {@link org.gearvrf.GVRActivity#registerView(View)} to add the {@link GVRView} to Android hierarchy view.
 * After it has been registered, the UI Thread will call {@link GVRView#draw(Canvas)} to refresh the
 * view as necessary. To draw the {@link GVRView} into its {@link GVRViewSceneObject} is necessary
 * override {@link GVRView#draw(Canvas)} and call the superclass version replacing the given
 * {@link Canvas} by other one provided by {@link GVRViewSceneObject#lockCanvas()}. After drawing
 * into the {@link GVRViewSceneObject}'s {@link Canvas}, the caller must invoke
 * {@link GVRViewSceneObject#unlockCanvasAndPost} to post the new contents to the scene object.
 * 
 * <pre>
 * {@code 
 * public void draw(Canvas canvas) {
 *     if (mSceneObject == null) return;
 *     Canvas glAttachedCanvas = mSceneObject.lockCanvas();
 *     super.draw(glAttachedCanvas);
 *     mSceneObject.unlockCanvasAndPost(glAttachedCanvas);
 *   }
 * }
 * </pre>
 *
 * Once you are done with a particular instance you need to call
 * {@link org.gearvrf.GVRActivity#unregisterView(View)} on it to avoid memory leaks.
 *
 * See {@link GVRWebView} and {@link GVRViewSceneObject}
 */
@Deprecated
public interface GVRView {
    /**
     * {@linkplain GVRViewSceneObject The scene object} created to this {@linkplain GVRView view}
     * will call {@link GVRView#setSceneObject(GVRViewSceneObject)} to set itself to the view.
     *
     * @param sceneObject {@link GVRViewSceneObject} created to this view.
     */
    void setSceneObject(GVRViewSceneObject sceneObject);

    /**
     * {@link GVRViewSceneObject} will call it to get {@linkplain View the view}.
     *
     * @return Returns GVRView instance as {@linkplain View Android view}.
     */
    View getView();
}
