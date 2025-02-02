##Syncthing on Android

This app aims to be a full featured [Syncthing](https://syncthing.net/) client comparable to the web ui.

Secondary objectives include:

* Showcase real world usage of:
  * [Mortar](https://github.com/square/mortar) + [Dagger2](https://github.com/google/dagger)
  * [RxJava](https://github.com/ReactiveX/RxJava) + [Retrolamba](https://github.com/orfjackal/retrolambda)
* CGO support for Syncthing on android.

###Building

#####Syncthing binary

Requirements

* You'll need build-essential or base-devel (including i386 support)
```bash
sudo apt-get install libc6:i386 libstdc++6:i386 lib32z1 libsdl1.2debian:i386
```
* Android sdk. Set `ANDROID_HOME`
```bash
export ANDROID_HOME=/opt/android/sdk
# Install Android SDK Tools
# Install Android SDK Platform-tools
# Install Android SDK Build-tools, revision 22.0.1
# Install Android Support Library, revision 22.1
# Install Android Support Repository, revision 13
./android list sdk --all
./android update sdk --all -t "<selected nr1,nr2,...>" -u
```

* Android ndk. Set `TOOLCHAIN_ROOT` or update scripts to point to yours
```bash
# Create standalone ARM toolchain
export NDK_ROOT=/opt/android/ndk/toolchain-arm
export TOOLCHAIN_ROOT=$NDK_ROOT
./android-ndk-r10d/build/tools/make-standalone-toolchain.sh --platform=android-21 --install-dir=$NDK_ROOT --arch=arm
```

* Building Syncthing
```bash
# You only need to run these once (or whenever the submodules are updated)

# Build go for android
./make-go.bash
# Build syncthing for android
./make-syncthing.bash
```

Alternatively use docker

```bash
#Make the image (only need to run once)
./make-docker-image.bash
#Build syncthing (only need to run once or whenever the submodules are updated)
./make-syncthing-docker.bash
```

#####App

```bash
# Build apk
./gradlew app:assembleDebug
```

###Contributing

Project is managed through gerrit `review.opensilk.org`

Easy way

```bash
mkdir OpenSilk && cd OpenSilk
repo init -u https://github.com/OpenSilk/Manifest.git
repo sync
cd SyncthingAndroid
repo start mybranch .
#make changes
repo upload .
```

Hard way
[git push](https://gerrit-review.googlesource.com/Documentation/user-upload.html#_git_push)

Pull requests are ignored
