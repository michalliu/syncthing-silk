/*
 * Copyright (c) 2015 OpenSilk Productions LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package syncthing.android.ui.session;

import android.annotation.TargetApi;
import android.os.Build;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentManager;
import android.widget.EditText;

import org.opensilk.common.core.mortar.HasScope;

import java.util.List;

import javax.inject.Inject;

import mortar.Presenter;
import mortar.bundler.BundleService;
import syncthing.android.R;
import syncthing.api.SessionScope;

/**
 * Created by drew on 3/16/15.
 */
@SessionScope
public class SessionFragmentPresenter extends Presenter<SessionFragmentPresenter.Fragment> {

    public interface Fragment extends HasScope {
        FragmentManager getChildFragmentManager();
    }

    @Inject
    public SessionFragmentPresenter() {
    }

    @Override
    protected BundleService extractBundleService(Fragment view) {
        return BundleService.getBundleService(view.getScope());
    }

    public FragmentManager getFragmentManager() {
        if(!hasView()) throw new IllegalStateException("Called after destoy");
        return getView().getChildFragmentManager();
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public void openDialogFragment(DialogFragment fragment, String tag) {
        fragment.show(getFragmentManager(), tag);
    }

    public void setFolderPath(String path) {
        if(!hasView()) return;
        List<android.support.v4.app.Fragment> fragments = getFragmentManager().getFragments();
        for (android.support.v4.app.Fragment fragment : fragments) {
            if (fragment.getTag() != "folderedit") continue;
            EditText text = (EditText) fragment.getView().findViewById(R.id.edit_folder_path);
            if (text == null) continue;
            text.setText(path);
            return;
        }
    }
}
