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

package org.gearvrf;

import java.util.ArrayList;
import java.util.List;

import org.gearvrf.io.CursorControllerListener;
import org.gearvrf.io.GVRInputManager;

/**
 * 
 * The input received from the {@link GVRCursorController} is dispatched across
 * the attached scene graph to activate the related sensors in the graph.
 * 
 * The {@link GVRBaseSensor} nodes provide the app with the {@link SensorEvent}s
 * generated as a result of the processing done by the
 * {@link GVRInputManagerImpl}.
 * 
 */
class GVRInputManagerImpl extends GVRInputManager {
    private static final String TAG = GVRInputManagerImpl.class.getSimpleName();
    private List<CursorControllerListener> listeners;
    private GVRScene scene;
    private List<GVRCursorController> controllers;

    GVRInputManagerImpl(GVRContext gvrContext, boolean useGazeCursorController,
                        boolean useAndroidWearTouchpad) {
        super(gvrContext, useGazeCursorController, useAndroidWearTouchpad);

        controllers = new ArrayList<GVRCursorController>();
        listeners = new ArrayList<CursorControllerListener>();

     }

    public void scanControllers()
    {
        super.scanControllers();
        for (GVRCursorController controller : super.getCursorControllers()) {
            addCursorController(controller);
        }
    }

    @Override
    public void addCursorControllerListener(CursorControllerListener listener) {
        synchronized (listeners) {
            listeners.add(listener);
        }
    }

    @Override
    public void removeCursorControllerListener(
            CursorControllerListener listener) {
        synchronized (listeners) {
            listeners.remove(listener);
        }
    }

    @Override
    public void addCursorController(GVRCursorController controller) {
        controllers.add(controller);
        synchronized (listeners) {
            for (CursorControllerListener listener : listeners) {
                listener.onCursorControllerAdded(controller);
            }
        }
    }

    @Override
    public void activateCursorController(GVRCursorController controller) {
        synchronized (listeners) {
            for (CursorControllerListener listener : listeners) {
                listener.onCursorControllerActive(controller);
            }
        }
    }

    @Override
    public void deactivateCursorController(GVRCursorController controller) {
        synchronized (listeners) {
            for (CursorControllerListener listener : listeners) {
                listener.onCursorControllerInactive(controller);
            }
        }
    }

    @Override
    public void removeCursorController(GVRCursorController controller) {
        controllers.remove(controller);
        controller.setScene(null);
        synchronized (listeners) {
            for (CursorControllerListener listener : listeners) {
                listener.onCursorControllerRemoved(controller);
            }
        }
    }


    /**
     * This method sets a new scene for the {@link GVRInputManagerImpl}
     * 
     * @param scene
     * 
     */
    void setScene(GVRScene scene) {
        this.scene = scene;

        for (GVRCursorController controller : controllers) {
            controller.setScene(scene);
            controller.invalidate();
        }
    }

    @Override
    protected void close() {
        if (controllers.size() > 0)
        {
            controllers.get(0).removePickEventListener(GVRBaseSensor.getPickHandler());
        }
        super.close();
        controllers.clear();
    }

    @Override
    public List<GVRCursorController> getCursorControllers() {
        return controllers;
    }
}