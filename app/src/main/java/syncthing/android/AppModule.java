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

package syncthing.android;

import android.app.AlarmManager;
import android.app.NotificationManager;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.wifi.WifiManager;

import org.opensilk.common.core.dagger2.ForApplication;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

/**
 * Created by drew on 3/4/15.
 */
@Module
public class AppModule {
    final App app;

    public AppModule(App app) {
        this.app = app;
    }

    @Provides @Singleton @ForApplication
    public Context provideAppContext() {
        return app;
    }


    @Provides @Singleton
    public ConnectivityManager provideConnectivityManager() {
        return (ConnectivityManager) app.getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    @Provides @Singleton
    public WifiManager provideWifiManager() {
        return (WifiManager) app.getSystemService(Context.WIFI_SERVICE);
    }

    @Provides @Singleton
    public NotificationManager provideNotificationManager() {
        return (NotificationManager) app.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    @Provides @Singleton
    public AlarmManager provideAlarmManager() {
        return (AlarmManager) app.getSystemService(Context.ALARM_SERVICE);
    }
}
