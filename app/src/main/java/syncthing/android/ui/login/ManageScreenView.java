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

import android.content.Context;
import android.support.v7.widget.LinearLayoutManager;
import android.util.AttributeSet;
import android.widget.RelativeLayout;

import org.opensilk.common.core.mortar.DaggerService;

import java.util.List;

import butterknife.ButterKnife;
import butterknife.InjectView;
import butterknife.OnClick;
import syncthing.android.R;
import syncthing.android.model.Credentials;
import syncthing.android.ui.common.CardRecyclerView;

/**
 * Created by drew on 3/15/15.
 */
public class ManageScreenView extends RelativeLayout {

    @InjectView(R.id.recyclerview) CardRecyclerView list;

    ManageScreenAdapter adapter;
    ManagePresenter presenter;

    public ManageScreenView(Context context, AttributeSet attrs) {
        super(context, attrs);
        if (isInEditMode())
            return;
        presenter = DaggerService.<ManageComponent>getDaggerComponent(getContext()).presenter();
        adapter = new ManageScreenAdapter();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        ButterKnife.inject(this);
        if (isInEditMode())
            return;
        list.setAdapter(adapter);
        list.setLayoutManager(new LinearLayoutManager(getContext()));
        presenter.takeView(this);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        presenter.dropView(this);
    }

    @OnClick(R.id.btn_done)
    public void onDone() {
        presenter.exitActivity();
    }

    @OnClick(R.id.btn_add)
    public void onAddDevice() {
        presenter.openAddScreen();
    }

    void load(List<Credentials> creds, Credentials def) {
        adapter.setDevices(creds, def);
    }
}
