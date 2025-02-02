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

package syncthing.android.ui.login;

import android.widget.Checkable;

import syncthing.android.R;
import syncthing.android.model.Credentials;
import syncthing.android.ui.common.Card;

/**
 * Created by drew on 3/15/15.
 */
public class ManageDeviceCard extends Card implements Checkable {

    public final Credentials credentials;

    boolean checked = false;

    public ManageDeviceCard(Credentials credentials) {
        this.credentials = credentials;
    }

    @Override
    public int getLayout() {
        return R.layout.login_manage_device;
    }

    @Override
    public void setChecked(boolean checked) {
        this.checked = checked;
    }

    @Override
    public boolean isChecked() {
        return checked;
    }

    @Override
    public void toggle() {
        setChecked(!checked);
    }
}
