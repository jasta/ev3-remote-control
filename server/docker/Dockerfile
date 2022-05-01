FROM ev3dev/debian-stretch-cross
ARG DEBIAN_FRONTEND=noninteractive

WORKDIR /
COPY common.sh lib.sh /
RUN sudo /common.sh

COPY cmake.sh /
RUN sudo /cmake.sh

COPY xargo.sh /
RUN sudo /xargo.sh

RUN sudo apt-get update
RUN sudo apt-get install --assume-yes --no-install-recommends \
    g++-arm-linux-gnueabi \
    crossbuild-essential-armel \
    libc6-dev-armel-cross

#COPY qemu.sh /
#RUN sudo /qemu.sh arm

ENV CARGO_TARGET_ARMV5TE_UNKNOWN_LINUX_GNUEABI_LINKER=arm-linux-gnueabi-gcc \
    CARGO_TARGET_ARMV5TE_UNKNOWN_LINUX_GNUEABI_RUNNER=qemu-arm \
    CC_armv5te_unknown_linux_gnueabi=arm-linux-gnueabi-gcc \
    CXX_armv5te_unknown_linux_gnueabi=arm-linux-gnueabi-g++ \
    QEMU_LD_PREFIX=/usr/arm-linux-gnueabi \
    RUST_TEST_THREADS=1
