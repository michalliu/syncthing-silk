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

package syncthing.api;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.inject.Named;

import rx.Observable;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action0;
import rx.functions.Action1;
import rx.subjects.BehaviorSubject;
import syncthing.api.model.Completion;
import syncthing.api.model.Config;
import syncthing.api.model.ConfigStats;
import syncthing.api.model.ConnectionInfo;
import syncthing.api.model.Connections;
import syncthing.api.model.DeviceConfig;
import syncthing.api.model.DeviceStats;
import syncthing.api.model.DeviceStatsMap;
import syncthing.api.model.Event;
import syncthing.api.model.FolderConfig;
import syncthing.api.model.FolderDeviceConfig;
import syncthing.api.model.FolderStats;
import syncthing.api.model.FolderStatsMap;
import syncthing.api.model.GUIConfig;
import syncthing.api.model.GuiError;
import syncthing.api.model.GuiErrors;
import syncthing.api.model.Ignores;
import syncthing.api.model.Model;
import syncthing.api.model.OptionsConfig;
import syncthing.api.model.Report;
import syncthing.api.model.SystemInfo;
import syncthing.api.model.Version;
import timber.log.Timber;

/**
 * Created by drew on 3/4/15.
 */
@SessionScope
public class SessionController implements EventMonitor.EventListener {

    public enum Change {
        ONLINE,
        OFFLINE,
        FAILURE,
        MODEL,
        MODEL_STATE,
        COMPLETION,
        FOLDER_STATS,
        DEVICE_STATS,
        CONNECTIONS_UPDATE,
        CONNECTIONS_CHANGE,
        DEVICE_REJECTED,
        FOLDER_REJECTED,
        CONFIG_UPDATE,
        SYSTEM,
        NOTICE,
        //EventMonitor
        NEED_LOGIN,

    }

    public static class ChangeEvent {
        public static final String NONE = "";
        public final Change change;
        // Id or name of object being effected
        // mostly useful for model updates
        public final String id;

        public ChangeEvent(Change change, String id) {
            this.change = change;
            this.id = id == null ? NONE : id;
        }

    }

    boolean isLocal;
    SystemInfo systemInfo;
    String myID;
    Config config;
    boolean configInSync;
    Connections connections = new Connections();
    DeviceStatsMap deviceStats = DeviceStatsMap.EMPTY;
    FolderStatsMap folderStats = FolderStatsMap.EMPTY;
    Version version;
    Report report;
    Map<String, Model> models = new LinkedHashMap<>(10);
    Map<String, FolderConfig> folders = new LinkedHashMap<>(10);
    List<DeviceConfig> devices = Collections.emptyList();
    Map<String, Map<String, Integer>> completion = new LinkedHashMap<>(10);
    Map<String, Event> deviceRejections = new LinkedHashMap<>();
    Map<String, Event> folderRejections = new LinkedHashMap<>();
    GuiErrors errorsList;

    long prevDate;
    boolean online;
    boolean restarting;
    boolean running;
    Subscription onlineSub;
    Subscription subspendSubscription;
    Subscription periodicRefreshSubscription;
    Subscription onLocalIndexUpdatedSubscription;

    final SyncthingApi restApi;
    final EventMonitor eventMonitor;
    final BehaviorSubject<ChangeEvent> changeBus = BehaviorSubject.create();

    @Inject
    public SessionController(boolean isLocal, SyncthingApi restApi, @Named("longpoll") SyncthingApi longpollRestApi) {
        Timber.i("new SessionController");
        this.isLocal = isLocal;
        this.restApi = restApi;
        this.eventMonitor = new EventMonitor(longpollRestApi, this);
    }

    public void init() {
        if (subspendSubscription != null
                && !subspendSubscription.isUnsubscribed()) {
            subspendSubscription.unsubscribe();
        } else if (!eventMonitor.isRunning()) {
            eventMonitor.start();
        }
        if (isOnline()) {
            setupPeriodicRefresh();
        }
        running = true;
    }

    public void suspend() {
        if (subspendSubscription != null) {
            subspendSubscription.unsubscribe();
        }
        // add delay to allow for configuration changes
        subspendSubscription = Observable.timer(30, TimeUnit.SECONDS)
                .subscribe(ii -> {
                    if (eventMonitor.isRunning()) {
                        eventMonitor.stop();
                    }
                });
        cancelPeriodicRefresh();
        running = false;
    }

    public void kill() {
        if (subspendSubscription != null) {
            subspendSubscription.unsubscribe();
        }
        eventMonitor.stop();
        cancelPeriodicRefresh();
        running = false;
    }

    public boolean isLocal() {
        return isLocal;
    }

    public boolean isRunning() {
        return running;
    }

    public void handleEvent(Event e) {
        if (updateState(true)) {
            switch (e.type) {
                case DEVICE_REJECTED:
                case FOLDER_REJECTED:
                    break; //Don't know of another way to get these so pass them through
                default:
                    Timber.d("Eating event %s", e.type);
                    return;//Eat event
            }
        }
        Timber.d("New event %s", e.type);
        switch (e.type) {
            case STARTUP_COMPLETE: {
                break;
            } case STATE_CHANGED: {
                if (models.containsKey(e.data.folder)) {
                    models.get(e.data.folder).state = e.data.to;
                    postChange(Change.MODEL_STATE, e.data.folder);
                } else {
                    refreshFolder(e.data.folder);
                }
                break;
            } case LOCAL_INDEX_UPDATED: {
                onLocalIndexUpdated(e);
                break;
            } case REMOTE_INDEX_UPDATED: {
                //pass
                break;
            } case DEVICE_CONNECTED: {
                refreshConnections();
                refreshDeviceStats();
                break;
            } case DEVICE_DISCONNECTED: {
                refreshConnections();
                refreshDeviceStats();
                break;
            } case DEVICE_REJECTED: {
                deviceRejections.put(e.data.device, e);
                postChange(Change.DEVICE_REJECTED);
                break;
            } case FOLDER_REJECTED: {
                folderRejections.put(e.data.folder + "-" + e.data.device, e);
                postChange(Change.FOLDER_REJECTED);
                break;
            } case CONFIG_SAVED: {
                refreshConfig();
                break;
            } case DOWNLOAD_PROGRESS: {
                //TODO
                break;
            } case FOLDER_SUMMARY: {
                updateModel(e.data.folder, e.data.summary);
                postChange(Change.MODEL, e.data.folder);
                break;
            } case FOLDER_COMPLETION: {
                updateCompletion(e.data.device, e.data.folder, new Completion(e.data.completion));
                postChange(Change.COMPLETION);
                break;
            } case PING: {
                //refreshSystem();
                break;
            } default: {
                break;
            }
        }
    }

    @Override
    public void onError(EventMonitor.Error e) {
        Timber.w("onError %s", e.toString());
        switch (e) {
            case UNAUTHORIZED:
                updateState(false);
                postChange(Change.NEED_LOGIN);
                break;
            case DISCONNECTED:
                updateState(false);
                break;
            case STOPPING:
                running = false;
                updateState(false);
                postChange(Change.FAILURE);
                break;
        }
    }

    boolean updateState(boolean online) {
        //No state change dont eat event;
        if (this.online == online) return false;
        //New event came in while we are initializing, eat it
        if (online && onlineSub != null && !onlineSub.isUnsubscribed()) return true;
        if (online) {
            this.restarting = false;
            //Our online state depends on all these items
            //so we merge them together so we can defer
            //posting the ONLINE status until we have set
            //all the values, the extra toMap step
            //is to prevent sideeffects and synchronization errors
            onlineSub = Observable.merge(
                    restApi.system(),
                    restApi.config(),
                    restApi.configStatus(),
                    restApi.connections(),
                    restApi.deviceStats(),
                    restApi.folderStats(),
                    restApi.version(),
                    restApi.report()
            ).toMap(
                    Object::getClass
            ).observeOn(
                    AndroidSchedulers.mainThread()
            ).subscribe(
                    (map) -> {
                        updateSystemInfo((SystemInfo) map.get(SystemInfo.class));
                        updateConfig((Config) map.get(Config.class));
                        updateConfigStats((ConfigStats) map.get(ConfigStats.class));
                        updateConnections((Connections) map.get(Connections.class));
                        setDeviceStats((DeviceStatsMap) map.get(DeviceStatsMap.class));
                        updateFolderStats((FolderStatsMap) map.get(FolderStatsMap.class));
                        setVersion((Version) map.get(Version.class));
                        setReport((Report) map.get(Report.class));
                        this.online = true;
                    },
                    this::logException,
                    () -> {
                        setupPeriodicRefresh();
                        postChange(Change.ONLINE);
                    }
            );
        } else {
            this.online = false;
            if (onlineSub != null) onlineSub.unsubscribe();
            cancelPeriodicRefresh();
            postChange(Change.OFFLINE);
        }
        return true;
    }

    void postChange(Change change) {
        postChange(change, null);
    }

    void postChange(Change change, String id) {
        switch (change) {
            case OFFLINE:
            case NEED_LOGIN:
            case FAILURE:
                sendChangeEvent(new ChangeEvent(change, id));
                return;
            default:
                if (isOnline()) {
                    sendChangeEvent(new ChangeEvent(change, id));
                } else {
                    Timber.w("Dropping change %s while offline", change.toString());
                }
        }
    }

    void sendChangeEvent(ChangeEvent event) {
        changeBus.onNext(event);
    }

    public void refreshSystem() {
        Subscription s = restApi.system()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        this::updateSystemInfo,
                        this::logException,
                        () -> postChange(Change.SYSTEM)
                );
    }

    public void refreshConfig() {
        Subscription s = Observable.merge(
                restApi.config(),
                restApi.configStatus()
        ).toMap(
                Object::getClass
        ).observeOn(
                AndroidSchedulers.mainThread()
        ).subscribe(
                map -> {
                    updateConfig((Config) map.get(Config.class));
                    updateConfigStats((ConfigStats) map.get(ConfigStats.class));
                },
                this::logException,
                () -> postChange(Change.CONFIG_UPDATE)
        );
    }

    public void refreshConnections() {
        refreshConnections(false);
    }

    public void refreshConnections(boolean update) {
        Subscription s = restApi.connections()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        this::updateConnections,
                        this::logException,
                        () -> postChange(update ? Change.CONNECTIONS_UPDATE : Change.CONNECTIONS_CHANGE));
    }

    public void refreshDeviceStats() {
        Subscription s = restApi.deviceStats()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        stats -> this.deviceStats = stats,
                        this::logException,
                        () -> postChange(Change.DEVICE_STATS)
                );
    }

    public void refreshFolderStats() {
        Subscription s = restApi.folderStats()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        stats -> this.folderStats = stats,
                        this::logException,
                        () -> postChange(Change.FOLDER_STATS)
                );
    }

    public void refreshVersion() {
        Subscription s = restApi.version()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::setVersion, this::logException);
    }

    public void refreshReport() {
        Subscription s = restApi.report()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::setReport, this::logException);
    }

    public void refreshFolder(String name) {
        Timber.d("refreshFolder(%s)", name);
        Subscription s = restApi.model(name)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        model -> models.put(name, model),
                        this::logException,
                        () -> postChange(Change.MODEL)
                );
    }

    public void refreshFolders(Collection<String> names) {
        Timber.d("refreshFolders()");
        if (names.isEmpty()) {
            return;
        }
        //Group all the network calls so we only receive one completion
        //event this will prevent change observers from receiving
        //mutliple MODEL changes
        List<Observable<Map.Entry<String, Model>>> observables = new LinkedList<>();
        for (String name: names) {
            observables.add(Observable.zip(Observable.just(name), restApi.model(name), Pair::of));
        }
        Subscription s = Observable.merge(observables)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        entry -> models.put(entry.getKey(), entry.getValue()),
                        this::logException,
                        () -> postChange(Change.MODEL)
                );
    }

    public void refreshCompletion(String device, String folder) {
        Subscription s = restApi.completion(device, folder)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        (comp) -> {
                            updateCompletion(device, folder, comp);
                        },
                        this::logException,
                        () -> postChange(Change.COMPLETION)
                );
    }

    public void refreshCompletions(Collection<Map.Entry<String, String>> refreshers) {
        if (refreshers.isEmpty()) {
            return;
        }
        //Same as refreshFolders but hella ridiculous since we need too keep track
        //of both device and folder
        List<Observable<Map.Entry<Map.Entry<String, String>, Completion>>> observables = new LinkedList<>();
        for (Map.Entry<String, String> refresh : refreshers) {
            observables.add(Observable.zip(Observable.just(refresh),
                    restApi.completion(refresh.getKey(), refresh.getValue()), Pair::of));
        }
        Subscription s = Observable.merge(observables)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        (nestedentry) -> updateCompletion(nestedentry.getKey().getKey(),
                                nestedentry.getKey().getValue(), nestedentry.getValue()),
                        this::logException,
                        () -> postChange(Change.COMPLETION)
                );
    }


    void onLocalIndexUpdated(Event e) {
        if (onLocalIndexUpdatedSubscription != null &&
                !onLocalIndexUpdatedSubscription.isUnsubscribed()) {
            Timber.i("Ignoring LocalIndexUpdate... refresh in progress");
            return;
        }
        onLocalIndexUpdatedSubscription = Observable.timer(500, TimeUnit.MILLISECONDS, AndroidSchedulers.mainThread())
                .subscribe(
                        ii -> refreshFolderStats(),
                        this::logException
                );
    }

    public boolean isOnline() {
        return online;
    }

    public boolean isRestarting() {
        return restarting;
    }

    public SystemInfo getSystemInfo() {
        return systemInfo;
    }

    public String getMyID() {
        return myID;
    }

    public int getAnnounceServersTotal() {
        return systemInfo.announceServersTotal;
    }

    public List<String> getAnnounceServersFailed() {
        return systemInfo.announceServersFailed;
    }

    void updateSystemInfo(SystemInfo systemInfo) {
        this.myID = systemInfo.myID;
        this.systemInfo = systemInfo;
        this.systemInfo.announceServersTotal = 0;
        this.systemInfo.announceServersFailed.clear();
        if (systemInfo.extAnnounceOK != null) {
            this.systemInfo.announceServersTotal = systemInfo.extAnnounceOK.size();
            for (String server : systemInfo.extAnnounceOK.keySet()) {
                if (!systemInfo.extAnnounceOK.get(server)) {
                    this.systemInfo.announceServersFailed.add(server);
                }
            }
        }
    }

    public Config getConfig() {
        return config;
    }

    void updateConfig(Config config) {
        this.config = config;
        folders.clear();
        for (FolderConfig f : config.folders) {
            folders.put(f.id, f);
        }
        devices = config.devices;
        //TODO clear out device/folder rejections if found in config
    }

    public boolean isConfigInSync() {
        return configInSync;
    }

    void updateConfigStats(ConfigStats configStats) {
        this.configInSync = configStats.configInSync;
    }

    public ConnectionInfo getConnection(String id) {
        return connections.connections.get(id);
    }

    public ConnectionInfo getConnectionTotal() {
        return connections.total;
    }

    void updateConnections(Connections conns) {
        long now = System.currentTimeMillis();
        for (String key : conns.connections.keySet()) {
            ConnectionInfo newC = conns.connections.get(key);
            newC.deviceId = key;
            newC.lastUpdate = now;
            if (connections.connections.containsKey(key)) {
                ConnectionInfo oldC = connections.connections.get(key);
                long td = (now - oldC.lastUpdate) / 1000;
                if (td > 0) {
                    newC.inbps = Math.max(0, (newC.inBytesTotal - oldC.inBytesTotal) / td);
                    newC.outbps = Math.max(0, (newC.outBytesTotal - oldC.outBytesTotal) / td);
                }
            }
            //also update completion
            if (!StringUtils.equals("total", key)) {
                if (!completion.containsKey(key)) {
                    completion.put(key, new HashMap<String, Integer>());
                }
                completion.get(key).put("_total", 100);
            }
        }
        connections = conns;
    }

    public DeviceStats getDeviceStats(String id) {
        return deviceStats.get(id);
    }

    void setDeviceStats(DeviceStatsMap deviceStats) {
        this.deviceStats = deviceStats;
    }

    public FolderStats getFolderStats(String name) {
        return folderStats.get(name);
    }

    void updateFolderStats(FolderStatsMap folderStats) {
        this.folderStats = folderStats;
    }

    public Version getVersion() {
        return version;
    }

    void setVersion(Version version) {
        this.version = version;
    }

    public Report getReport() {
        return report;
    }

    void setReport(Report report) {
        this.report = report;
    }

    @Nullable
    public Model getModel(String folderName) {
        return models.get(folderName);
    }

    void updateModel(String folderName, Model model) {
        Timber.d("updateModel(%s) m=%s", folderName, model);
        if (models.containsKey(folderName)) {
            models.remove(folderName);
        }
        models.put(folderName, model);
    }

    public FolderConfig getFolder(String name) {
        return folders.get(name);
    }

    public Collection<FolderConfig> getFolders() {
        return folders.values();
    }

    public List<DeviceConfig> getDevices() {
        return devices;
    }

    @Nullable
    public DeviceConfig getThisDevice() {
        for (DeviceConfig d : getDevices()) {
            if (StringUtils.equals(d.deviceID, myID)){
                return d;
            }
        }
        return null;
    }

    @NonNull
    public List<DeviceConfig> getRemoteDevices() {
        List<DeviceConfig> dvs = new ArrayList<>();
        for (DeviceConfig d : getDevices()) {
            if (!StringUtils.equals(d.deviceID, myID)) {
                dvs.add(d);
            }
        }
        return dvs;
    }

    @Nullable
    public DeviceConfig getDevice(String deviceId) {
        for (DeviceConfig d : getDevices()) {
            if (StringUtils.equals(deviceId, d.deviceID)) {
                return d;
            }
        }
        return null;
    }

    void updateCompletion(String device, String folder, Completion comp) {
        Timber.d("Updating completion for %s", device);
        if (!completion.containsKey(device)) {
            completion.put(device, new HashMap<String, Integer>());
        }
        completion.get(device).put(folder, Math.round(comp.completion));
        float tot = 0;
        int cnt = 0;
        for (String key : completion.get(device).keySet()) {
            if ("_total".equals(key)) continue;
            tot += completion.get(device).get(key);
            cnt++;
        }
        completion.get(device).put("_total", Math.min(100, Math.round(tot / cnt)));
    }

    public int getCompletionTotal(String deviceId) {
        if (completion.containsKey(deviceId)) {
            return completion.get(deviceId).get("_total");
        } else {
            return -1;
        }
    }

    @Nullable
    public Map<String, Integer> getCompletionStats(String deviceId) {
        return completion.get(deviceId);
    }

    public Set<Map.Entry<String, Event>> getDeviceRejections() {
        return deviceRejections.entrySet();
    }

    public void removeDeviceRejection(String key) {
        deviceRejections.remove(key);
        postChange(Change.DEVICE_REJECTED);
    }

    public Set<Map.Entry<String, Event>> getFolderRejections() {
        return folderRejections.entrySet();
    }

    public void removeFolderRejection(String key) {
        folderRejections.remove(key);
        postChange(Change.FOLDER_REJECTED);
    }

    public void refreshErrors() {
        Subscription s = restApi.errors()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        errors -> errorsList = errors,
                        this::logException,
                        () -> postChange(Change.NOTICE)
                );
    }

    public void clearErrors() {
        restApi.clearErrors().subscribe(
                v -> {},
                this::logException,
                () -> {
                    errorsList = null;
                    postChange(Change.NOTICE);
                });
    }

    @Nullable
    public GuiError getLatestError() {
        if (errorsList != null
                && errorsList.errors != null
                && !errorsList.errors.isEmpty()) {
            return errorsList.errors.get(errorsList.errors.size()-1);
        }
        return null;
    }

    public Subscription editFolder(FolderConfig folder, Action1<Throwable> onError, Action0 onComplete) {
        return restApi.config()
                .map((config) -> {
                    if (config.folders.isEmpty()) {
                        config.folders = Collections.singletonList(folder);
                    } else {
                        int index = config.folders.indexOf(folder);
                        if (index < 0) {
                            config.folders.add(folder);
                        } else {
                            config.folders.set(index, folder);
                        }
                    }
                    return config;
                })
                .flatMap(restApi::updateConfig)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        v -> {},
                        onError,
                        onComplete
                );
    }

    public Subscription deleteFolder(FolderConfig folder, Action1<Throwable> onError, Action0 onComplete) {
        return restApi.config()
                .map((config) -> {
                    if (!config.folders.remove(folder)) {
                        throw new IllegalArgumentException("Folder " + folder.id + " not found in config");
                    }
                    config.folders.remove(folder);
                    return config;
                })
                .flatMap(restApi::updateConfig)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        v -> {},
                        onError,
                        onComplete
                );
    }

    public void shareFolder(String name, String devId, Action1<Throwable> onError, Action0 onComplete) {
        restApi.config().zipWith(restApi.deviceId(devId),
                (config, deviceId) -> {
                    if (deviceId.id == null) {
                        throw new NullPointerException(deviceId.error);
                    }
                    boolean folderUpdated = false;
                    for (FolderConfig folder : config.folders) {
                        if (StringUtils.equals(folder.id, name)) {
                            if (folder.devices.isEmpty()) {
                                folder.devices = new ArrayList<>();
                            }
                            for (FolderDeviceConfig d : folder.devices) {
                                if (StringUtils.equals(d.deviceID, deviceId.id)) {
                                    throw new IllegalArgumentException("Folder already shared with device "
                                            + d.deviceID);
                                }
                            }
                            folder.devices.add(new FolderDeviceConfig(deviceId.id));
                            folderUpdated = true;
                            break;
                        }
                    }
                    if (!folderUpdated) {
                        throw new IllegalArgumentException("Folder doesn't exist " + name);
                    }
                    return config;
                })
                .flatMap(restApi::updateConfig)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        v -> {
                        },
                        onError,
                        onComplete
                );

    }

    public Subscription editDevice(DeviceConfig device, Map<String, Boolean> folders, Action1<Throwable> onError, Action0 onComplete) {
        return restApi.deviceId(device.deviceID)
                // fetched the normalized id
                // then we update the device with the new id
                // and update the config
                .zipWith(restApi.config(), (deviceId, config) -> {
                    if (deviceId.id == null) {
                        throw new NullPointerException(deviceId.error);
                    }
                    Timber.d("editDevice() updating deviceId %s -> %s", device.deviceID, deviceId.id);
                    device.deviceID = deviceId.id;

                    if (config.devices.isEmpty()) {
                        config.devices = Collections.singletonList(device);
                    } else {
                        int index = config.devices.indexOf(device);
                        if (index < 0) {
                            config.devices.add(device);
                        } else {
                            config.devices.set(index, device);
                        }
                    }
                    if (folders != null && !folders.isEmpty()) {
                        for (FolderConfig f : config.folders) {
                            if (folders.containsKey(f.id)) {
                                boolean wants = folders.get(f.id);
                                if (f.devices.isEmpty()) {
                                    f.devices = new ArrayList<>();
                                }
                                boolean found = false;
                                for (FolderDeviceConfig d : f.devices) {
                                    if (StringUtils.equals(d.deviceID, device.deviceID)) {
                                        found = true;
                                        break;
                                    }
                                }
                                if (found && !wants) {
                                    f.devices.remove(new FolderDeviceConfig(device.deviceID));
                                } else if (!found && wants) {
                                    f.devices.add(new FolderDeviceConfig(device.deviceID));
                                }
                            }
                        }
                    }
                    return config;
                })
                        // send our edited config back to the server
                .flatMap(restApi::updateConfig)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        v -> {
                        },
                        onError,
                        onComplete
                );
    }

    public Subscription deleteDevice(DeviceConfig device, Action1<Throwable> onError, Action0 onComplete) {
        return restApi.deviceId(device.deviceID)
                // got normalized device id
                // need to get config and remove device with matching
                .zipWith(restApi.config(), (deviceId, config) -> {
                    if (deviceId.id == null) {
                        throw new NullPointerException(deviceId.error);
                    }
                    Timber.d("deleteDevice() updating deviceId %s -> %s", device.deviceID, deviceId.id);
                    device.deviceID = deviceId.id;

                    if (!config.devices.remove(device)) {
                        throw new IllegalArgumentException("Device not found in config " + device.deviceID);
                    }
                    return config;
                })
                        // send our altered config back to server
                .flatMap(restApi::updateConfig)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        v -> {},
                        onError,
                        onComplete
                );
    }

    public void ignoreDevice(String id, Action1<Throwable> onError, Action0 onComplete) {
        restApi.deviceId(id)
                .zipWith(restApi.config(), (deviceId, config) -> {
                    if (deviceId.id == null) {
                        throw new NullPointerException(deviceId.error);
                    }
                    if (!config.ignoredDevices.contains(deviceId.id)) {
                        config.ignoredDevices.add(deviceId.id);
                    }
                    return config;
                })
                .flatMap(restApi::updateConfig)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        v -> {},
                        onError,
                        onComplete
                );
    }

    public Subscription editSettings(DeviceConfig thisDevice, OptionsConfig options,
                                     GUIConfig guiConfig, Action1<Throwable> onError, Action0 onComplete) {
        return restApi.config()
                .map(config -> {
                    int idx = config.devices.indexOf(thisDevice);
                    config.devices.set(idx, thisDevice);
                    config.options = options;
                    config.gui = guiConfig;
                    return config;
                })
                .flatMap(restApi::updateConfig)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        v -> {},
                        onError,
                        onComplete
                );
    }

    public void restart() {
        restarting = true;
        eventMonitor.resetCounter();
        updateState(false);
        restApi.restart().subscribe(v -> {}, this::logException);
    }

    public void shutdown() {
        restarting = false;
        eventMonitor.stop();
        updateState(false);
        restApi.shutdown().subscribe(v -> {}, this::logException);
    }

    public Observable<List<String>> getAutoCompleteDirectoryList(String current) {
        return restApi.autocompleteDirectory(current);
    }

    public Observable<Bitmap> getQRImage(String deviceId) {
        return restApi.qr(deviceId)
                .map(resp -> {
                    InputStream in = null;
                    try {
                        in = resp.getBody().in();
                        return BitmapFactory.decodeStream(in);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    } finally {
                        if (in != null) try {in.close();} catch (Exception ignored){}
                    }
                });
    }

    public Subscription overrideChanges(String id, Action1<Throwable> onError) {
        return restApi.override(id).observeOn(AndroidSchedulers.mainThread()).subscribe(
                (v) -> {},
                onError
        );
    }

    public Subscription scanFolder(String id, Action1<Throwable> onError) {
        return restApi.scan(id).observeOn(AndroidSchedulers.mainThread()).subscribe(
                (v) -> {},
                onError
        );
    }

    public Subscription getIgnores(String id, Action1<Ignores> onNext, Action1<Throwable> onError) {
        return restApi.ignores(id).observeOn(AndroidSchedulers.mainThread()).subscribe(
                onNext,
                onError
        );
    }

    public Subscription editIgnores(String id, Ignores ignores, Action1<Ignores> onNext, Action1<Throwable> onError, Action0 onComplete) {
        return restApi.updateIgnores(id, ignores).observeOn(AndroidSchedulers.mainThread()).subscribe(
                onNext,
                onError,
                onComplete
        );
    }

    void setupPeriodicRefresh() {
        cancelPeriodicRefresh();
        periodicRefreshSubscription = Observable.interval(30, TimeUnit.SECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(ii -> {
                    refreshSystem();
                    refreshConnections(true);
                    refreshErrors();
                });
    }

    void cancelPeriodicRefresh() {
        if (periodicRefreshSubscription != null) {
            periodicRefreshSubscription.unsubscribe();
        }
    }

    void logException(Throwable e) {
        Timber.e("%s: %s", e.getClass().getSimpleName(), e.getMessage(), e);
//        if (e instanceof RetrofitError) {
//            RetrofitError re = (RetrofitError)e;
//            int status = re.getResponse().getStatus();
//            if (status >= 400 && status <= 599) {
//                updateState(false);
//                //TODO notify offline
//            }
//        }
    }

    public Subscription subscribeChanges(Action1<ChangeEvent> onNext, Change... changes) {
        Observable<ChangeEvent> o;
        if (changes.length == 0) {
            o = changeBus.asObservable();
        } else {
            o = changeBus.asObservable().filter(c -> {
                for (Change cc : changes) {
                    if (c.change == cc) return true;
                }
                return false;
            });
        }
        onNext.call(new ChangeEvent(online ? Change.ONLINE : Change.OFFLINE, null));
        return o.observeOn(AndroidSchedulers.mainThread()).subscribe(onNext);
    }

}
