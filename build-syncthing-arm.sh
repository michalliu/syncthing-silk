#!/bin/bash -e
# Make a different copy for x86 and ARM (See https://github.com/golang/go/issues/8161 and https://github.com/golang/go/issues/9114)
unset GOROOT

# Build the syncthing library
ORIG=$(pwd)
mkdir -p bin

# Load submodules
if [ ! -e "syncthing/src/github.com/syncthing/syncthing/.git" ]; then
        git submodule update --init --recursive
fi

# Check for GOLANG installation
if [ -z $GOROOT ] || [[ $(go version) != go\ version\ go1.4* ]] ; then
        mkdir -p "build"
        tmpgo='build/cgo-arm'
        if [ ! -f "$tmpgo/bin/go" ]; then
                # Download GOLANG v1.4.1
                wget -O go.src.tar.gz http://storage.googleapis.com/golang/go1.4.2.src.tar.gz
                sha1=$(sha1sum go.src.tar.gz)
                if [ "$sha1" != "460caac03379f746c473814a65223397e9c9a2f6  go.src.tar.gz" ]; then
                        echo "go.src.tar.gz SHA1 checksum does not match!"
                        exit 1
                fi
                mkdir -p $tmpgo
                tar -xzf go.src.tar.gz --strip=1 -C $tmpgo
                rm go.src.tar.gz
                # Build GO for host
                pushd $tmpgo/src
                ./make.bash --no-clean
                popd
        fi

        # Add GO to the environment
        export GOROOT="$(pwd)/$tmpgo"
fi

if [ -z $ANDROID_NDK ]; then
  echo 'Missing $ANDROID_NDK (https://developer.android.com/tools/sdk/ndk/)'
  exit 1
fi

# Add GO compiler to PATH
export PATH=$GOROOT/bin:$PATH

# Prepare GOLANG to cross-compile for android ARM. Requires NDK with gcc.
ndkarm=$(pwd)/build/ndk-arm
if [ ! -f $ndkarm/arm-linux-androideabi/bin/gcc ] || [ ! -f $GOROOT/bin/android_arm/go ]; then
        ABI=arch-arm $ANDROID_NDK/build/tools/make-standalone-toolchain.sh --platform=android-9 --install-dir=$ndkarm --arch=arm --toolchain=arm-linux-androideabi-4.9
        pushd $GOROOT/src
        # KNOWN TO FAIL: https://github.com/MarinX/godroid
        set +e
        CC_FOR_TARGET=$ndkarm/bin/arm-linux-androideabi-gcc CGO_ENABLED=1 GOOS=android GOARCH=arm GOARM=5 ./make.bash --no-clean
        set -e
        popd
fi

# Setup GOPATH
cd "syncthing/"
export GOPATH="$(pwd)"

# Install godep
$GOROOT/bin/go get github.com/tools/godep
export PATH="$(pwd)/bin":$PATH

# Setup syncthing and clean
export ENVIRONMENT=android
pushd src/github.com/syncthing/syncthing
$GOROOT/bin/go run build.go clean

# Patch Syncthing
git am -3 ../../../../../patches/*

# ARM-Android
PATH=$ndkarm/arm-linux-androideabi/bin:$PATH CGO_ENABLED=1 GOARM=5 go run build.go -goos android -goarch arm -no-upgrade build
mkdir -p $ORIG/app/libs/armeabi
mv syncthing $ORIG/app/libs/armeabi/libsyncthing.so
$GOROOT/bin/go run build.go clean
popd

