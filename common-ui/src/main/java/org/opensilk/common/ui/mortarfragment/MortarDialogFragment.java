/*
 * Copyright (C) 2015 OpenSilk Productions LLC
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

package org.opensilk.common.ui.mortarfragment;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v7.app.AppCompatDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.opensilk.common.ui.mortar.LayoutCreator;
import org.opensilk.common.ui.mortar.Screen;
import org.opensilk.common.ui.mortar.ScreenScoper;

import mortar.MortarScope;
import timber.log.Timber;

/**
 * Created by drew on 3/16/15.
 */
public abstract class MortarDialogFragment extends DialogFragment {
    private static final boolean DEBUG_LIFECYCLE = true;

    private MortarScope mScope;
    private Screen mScreen;

    protected abstract Screen newScreen();

    protected boolean shouldRetainScope() {
        return true;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        if (DEBUG_LIFECYCLE) Timber.v("->onCreate %s", getScopeName());
        super.onCreate(savedInstanceState);
        mScope = findOrMakeScope();
        setRetainInstance(shouldRetainScope());
        if (DEBUG_LIFECYCLE) Timber.v("<-onCreate %s", getScopeName());
    }

    @NonNull @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        return new AppCompatDialog(mScope.createContext(getActivity()), getTheme());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        LayoutCreator layoutCreator = LayoutCreator.getService(getActivity());
        //Watch out, inflater here is assumed to be from the dialogs context
        return inflater.inflate(layoutCreator.getLayout(getScreen()), container, false);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        if (DEBUG_LIFECYCLE) Timber.v("->onActivityCreated %s", getScopeName());
        super.onActivityCreated(savedInstanceState);
        if (DEBUG_LIFECYCLE) Timber.v("<-onActivityCreated %s", getScopeName());
    }

    @Override
    public void onDestroy() {
        if (DEBUG_LIFECYCLE) Timber.v("->onDestroy %s", getScopeName());
        super.onDestroy();
        if (mScope != null) {
            Timber.d("Destroying fragment scope %s", getScopeName());
            mScope.destroy();
            mScope = null;
        }
        if (DEBUG_LIFECYCLE) Timber.v("<-onDestroy %s", getScopeName());
    }

    protected MortarScope findOrMakeScope() {
        MortarScope scope = MortarScope.findChild(getActivity(), getScopeName());
        if (scope != null) {
            if (scope.isDestroyed()) {
                Timber.d("Recreating destroyed fragment scope %s", getScopeName());
                scope = null;
            } else {
                Timber.d("Reusing fragment scope %s", getScopeName());
            }
        }
        if (scope == null) {
            ScreenScoper scoper = ScreenScoper.getService(getActivity());
            scope = scoper.getScreenScope(getActivity(), getScopeName(), getScreen());
            Timber.d("Created new fragment scope %s", getScopeName());
        }
        return scope;
    }

    //Enforce using screen name as scope name, this is also used to tag the fragment
    public final String getScopeName() {
        return getScreen().getName();
    }

    @NonNull
    public final MortarScope getScope() {
        if (mScope == null) {
            throw new IllegalStateException("Can't call getScope() before onCreate()");
        }
        return mScope;
    }

    @NonNull
    public final Screen getScreen() {
        if (mScreen == null) {
            mScreen = newScreen();
        }
        return mScreen;
    }

    public static <T extends MortarDialogFragment> T factory(Context context, String name, Bundle args) {
        return (T) Fragment.instantiate(context, name, args);
    }

}
