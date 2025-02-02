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

package syncthing.api.model;

import java.io.Serializable;

import syncthing.api.ApiUtils;

/**
 * Created by drew on 3/1/15.
 */
public class DeviceConfig implements Serializable {
    private static final long serialVersionUID = 2383227051854131929L;
    public String deviceID;
    public String name;
    public String[] addresses;
    public Compression compression;
    public String certName;
    public boolean introducer;

    public static DeviceConfig withDefaults() {
        DeviceConfig d = new DeviceConfig();
        d.addresses = new String[]{"dynamic"};
        d.compression = Compression.METADATA;
        d.introducer = false;
        return d;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DeviceConfig device = (DeviceConfig) o;

        if (deviceID != null ? !deviceID.equals(device.deviceID) : device.deviceID != null)
            return false;

        return true;
    }

    @Override
    public int hashCode() {
        return deviceID != null ? deviceID.hashCode() : 0;
    }

    @Override
    public String toString() {
        return ApiUtils.reflectionToString(this);
    }
}
