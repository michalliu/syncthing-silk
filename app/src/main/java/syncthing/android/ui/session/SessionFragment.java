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

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import org.opensilk.common.core.mortar.DaggerService;
import org.opensilk.common.ui.mortar.ActionBarConfig;
import org.opensilk.common.ui.mortar.ActionBarOwner;
import org.opensilk.common.ui.mortar.Screen;
import org.opensilk.common.ui.mortarfragment.MortarFragment;

import java.util.List;

import syncthing.android.R;
import syncthing.android.model.Credentials;
import syncthing.android.ui.LauncherActivity;
import syncthing.android.ui.LauncherActivityComponent;
import syncthing.android.ui.common.ActivityRequestCodes;

/**
 * Created by drew on 3/11/15.
 */
public class SessionFragment extends MortarFragment implements SessionFragmentPresenter.Fragment {

    public static SessionFragment newInstance(Credentials credentials) {
        SessionFragment f = new SessionFragment();
        Bundle b = new Bundle(1);
        b.putParcelable("creds", credentials);
        f.setArguments(b);
        return f;
    }

    Credentials mCredentials;
    //Holding this reference is a hack to hook lifecycle
    //TODO add lifecycle hooks to fragmentPresenter and move actionbar stuff to SessionPresenter
    SessionPresenter mPresenter;
    SessionFragmentPresenter mFragmentPresenter;

    @Override
    protected Screen newScreen() {
        ensureCredentials();
        return new SessionScreen(mCredentials);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mPresenter = DaggerService.<SessionComponent>getDaggerComponent(getScope()).presenter();
        mFragmentPresenter = DaggerService.<SessionComponent>getDaggerComponent(getScope()).fragmentPresenter();
        mFragmentPresenter.takeView(this);
    }

    @Override
    public void onDestroy() {
        mFragmentPresenter.dropView(this);
        super.onDestroy();
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        //TODO stop doing this
        setHasOptionsMenu(true);
        ActionBarOwner actionBarOwner = DaggerService.<LauncherActivityComponent>
                getDaggerComponent(getActivity()).actionBarOwner();
        actionBarOwner.setConfig(actionBarOwner.getConfig().buildUpon().setTitle(mCredentials.alias).build());
    }

    @Override
    public void onStart() {
        super.onStart();
        mPresenter.controller.init();
    }

    @Override
    public void onStop() {
        super.onStop();
        mPresenter.controller.suspend();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.session, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (!mPresenter.controller.isOnline() || mPresenter.controller.getSystemInfo() == null)
            return false;
        switch (item.getItemId()) {
            case R.id.add_device:
                mPresenter.openAddDeviceScreen();
                return true;
            case R.id.add_folder:
                mPresenter.openAddFolderScreen();
                return true;
            case R.id.settings:
                mPresenter.openSettingsScreen();
                return true;
            case R.id.show_id: {
                Context context = getScope().createContext(getActivity());
                new ShowIdDialog(context).show();
                return true;
            } case R.id.shutdown: {
                Context context = getScope().createContext(getActivity());
                new AlertDialog.Builder(context)
                        .setTitle(R.string.shutdown)
                        .setMessage(R.string.are_you_sure_you_want_to_shutdown_syncthing)
                        .setPositiveButton(R.string.shutdown, (dialog, which) -> mPresenter.controller.shutdown())
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
                return true;
            } case R.id.restart:
                mPresenter.controller.restart();
                return true;
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    void ensureCredentials() {
        if (mCredentials == null) {
            getArguments().setClassLoader(getClass().getClassLoader());
            mCredentials = getArguments().getParcelable("creds");
        }
        if (mCredentials == null) {
            throw new NullPointerException("You forgot to supply credentials to the session");
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == ActivityRequestCodes.DIRECTORY_PICKER) {
            if (resultCode == Activity.RESULT_OK) {
                String path = data.getStringExtra("folder");
                mFragmentPresenter.setFolderPath(path);
            }
        }
    }
}
