#!/usr/bin/env bash
# ----
# Script to generate the detail graphs of a BenchmarkSQL run.
#
# Copyright (C) 2016, Denis Lussier
# Copyright (C) 2016, Jan Wieck
# ----

if [ $# -lt 1 ] ; then
    echo "usage: $(basename $0) RESULT_DIR [SKIP_MINUTES]" >&2
    exit 2
fi

if [ $# -gt 1 ] ; then
    SKIP=$2
else
    SKIP=-1
fi

RUN_DIR=`pwd`

WIDTH=12
HEIGHT=6
POINTSIZE=12

resdir="$1"
cd "${resdir}" || exit 1

if [ -f ./data/sys_info.csv ] ; then
    SIMPLE_GRAPHS="tpm_nopm latency latency_new_order cpu_utilization dirty_buffers"
else
    SIMPLE_GRAPHS="tpm_nopm latency latency_new_order"
fi

for graph in $SIMPLE_GRAPHS ; do
    echo -n "Generating ${resdir}/${graph}.svg ... "
    out=$(sed -e "s/@WIDTH@/${WIDTH}/g" \
          -e "s/@HEIGHT@/${HEIGHT}/g" \
          -e "s/@POINTSIZE@/${POINTSIZE}/g" \
          -e "s/@SKIP@/${SKIP}/g" \
          <${RUN_DIR}/misc/${graph}.R | R --no-save)
    if [ $? -ne 0 ] ; then
        echo "ERROR"
        echo "$out" >&2
        exit 3
    fi
    echo "OK"
done

for fname in ./data/blk_*.csv ; do
    if [ ! -f "${fname}" ] ; then
        continue
    fi
    devname=$(basename ${fname} .csv)

    echo -n "Generating ${resdir}/${devname}_iops.svg ... "
    out=$(sed -e "s/@WIDTH@/${WIDTH}/g" \
          -e "s/@HEIGHT@/${HEIGHT}/g" \
          -e "s/@POINTSIZE@/${POINTSIZE}/g" \
          -e "s/@SKIP@/${SKIP}/g" \
          -e "s/@DEVICE@/${devname}/g" <${RUN_DIR}/misc/blk_device_iops.R | R --no-save)
    if [ $? -ne 0 ] ; then
        echo "ERROR"
        echo "$out" >&2
        exit 3
    fi
    echo "OK"

    echo -n "Generating ${resdir}/${devname}_kbps.svn ... "
    out=$(sed -e "s/@WIDTH@/${WIDTH}/g" \
          -e "s/@HEIGHT@/${HEIGHT}/g" \
          -e "s/@POINTSIZE@/${POINTSIZE}/g" \
          -e "s/@SKIP@/${SKIP}/g" \
          -e "s/@DEVICE@/${devname}/g" <${RUN_DIR}/misc/blk_device_kbps.R | R --no-save)
    if [ $? -ne 0 ] ; then
        echo "ERROR"
        echo "$out" >&2
        exit 3
    fi
    echo "OK"
done

for fname in ./data/net_*.csv ; do
    if [ ! -f "${fname}" ] ; then
        continue
    fi
    devname=$(basename ${fname} .csv)

    echo -n "Generating ${resdir}/${devname}_iops.svn ... "
    out=$(sed -e "s/@WIDTH@/${WIDTH}/g" \
          -e "s/@HEIGHT@/${HEIGHT}/g" \
          -e "s/@POINTSIZE@/${POINTSIZE}/g" \
          -e "s/@SKIP@/${SKIP}/g" \
          -e "s/@DEVICE@/${devname}/g" <${RUN_DIR}/misc/net_device_iops.R | R --no-save)
    if [ $? -ne 0 ] ; then
        echo "ERROR"
        echo "$out" >&2
        exit 3
    fi
    echo "OK"

    echo -n "Generating ${resdir}/${devname}_kbps.svn ... "
    out=$(sed -e "s/@WIDTH@/${WIDTH}/g" \
          -e "s/@HEIGHT@/${HEIGHT}/g" \
          -e "s/@POINTSIZE@/${POINTSIZE}/g" \
          -e "s/@SKIP@/${SKIP}/g" \
          -e "s/@DEVICE@/${devname}/g" <${RUN_DIR}/misc/net_device_kbps.R | R --no-save)
    if [ $? -ne 0 ] ; then
        echo "ERROR"
        echo "$out" >&2
        exit 3
    fi
    echo "OK"
done

cd ..

