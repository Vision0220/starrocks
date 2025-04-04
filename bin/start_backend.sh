#!/usr/bin/env bash
# Copyright 2021-present StarRocks, Inc. All rights reserved.
#
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.

MACHINE_TYPE=$(uname -m)

# ================== parse opts =======================
curdir=`dirname "$0"`
curdir=`cd "$curdir"; pwd`

OPTS=$(getopt \
    -n $0 \
    -o '' \
    -l 'daemon' \
    -l 'cn' \
    -l 'be' \
-- "$@")

eval set -- "$OPTS"

RUN_DAEMON=0
RUN_CN=0
RUN_BE=0

while true; do
    case "$1" in
        --daemon) RUN_DAEMON=1 ; shift ;;
        --cn) RUN_CN=1; RUN_BE=0; shift ;;
        --be) RUN_BE=1; RUN_CN=0; shift ;;
        --) shift ;  break ;;
        *) ehco "Internal error" ; exit 1 ;;
    esac
done


# ================== conf section =======================
export STARROCKS_HOME=`cd "$curdir/.."; pwd`
source $STARROCKS_HOME/bin/common.sh

export_shared_envvars
if [ ${RUN_BE} -eq 1 ] ; then
    export_env_from_conf $STARROCKS_HOME/conf/be.conf
    export_mem_limit_from_conf $STARROCKS_HOME/conf/be.conf
fi
if [ ${RUN_CN} -eq 1 ]; then
    export_env_from_conf $STARROCKS_HOME/conf/cn.conf
    export_mem_limit_from_conf $STARROCKS_HOME/conf/cn.conf
fi

if [ $? -ne 0 ]; then
    exit 1
fi

export JEMALLOC_CONF="percpu_arena:percpu,oversize_threshold:0,muzzy_decay_ms:5000,dirty_decay_ms:5000,metadata_thp:auto,background_thread:true"
# enable coredump when BE build with ASAN
export ASAN_OPTIONS=abort_on_error=1:disable_coredump=0:unmap_shadow_on_exit=1
export LSAN_OPTIONS=suppressions=${STARROCKS_HOME}/conf/asan_suppressions.conf


# ================== jvm section =======================
if [ -e $STARROCKS_HOME/conf/hadoop_env.sh ]; then
    source $STARROCKS_HOME/conf/hadoop_env.sh
fi

# NOTE: JAVA_HOME must be configed if using hdfs scan, like hive external table
jvm_arch="amd64"
if [[ "${MACHINE_TYPE}" == "aarch64" ]]; then
    jvm_arch="aarch64"
fi

if [ "$JAVA_HOME" = "" ]; then
    export LD_LIBRARY_PATH=$STARROCKS_HOME/lib/jvm/$jvm_arch/server:$STARROCKS_HOME/lib/jvm/$jvm_arch:$LD_LIBRARY_PATH
else
    java_version=$(jdk_version)
    if [[ $java_version -gt 8 ]]; then
        export LD_LIBRARY_PATH=$JAVA_HOME/lib/server:$JAVA_HOME/lib:$LD_LIBRARY_PATH
        # JAVA_HOME is jdk
    elif [[ -d "$JAVA_HOME/jre"  ]]; then
        export LD_LIBRARY_PATH=$JAVA_HOME/jre/lib/$jvm_arch/server:$JAVA_HOME/jre/lib/$jvm_arch:$LD_LIBRARY_PATH
        # JAVA_HOME is jre
    else
        export LD_LIBRARY_PATH=$JAVA_HOME/lib/$jvm_arch/server:$JAVA_HOME/lib/$jvm_arch:$LD_LIBRARY_PATH
    fi
fi

# check java version and choose correct JAVA_OPTS
JAVA_VERSION=$(jdk_version)
final_java_opt=$JAVA_OPTS
if [[ "$JAVA_VERSION" -gt 8 ]]; then
    if [ -n "$JAVA_OPTS_FOR_JDK_9_AND_LATER" ]; then
        final_java_opt=$JAVA_OPTS_FOR_JDK_9_AND_LATER
    fi
fi

export LIBHDFS_OPTS=$final_java_opt
# Prevent JVM from handling any internally or externally generated signals.
# Otherwise, JVM will overwrite the signal handlers for SIGINT and SIGTERM.
export LIBHDFS_OPTS="$LIBHDFS_OPTS -Xrs"

# HADOOP_CLASSPATH defined in $STARROCKS_HOME/conf/hadoop_env.sh
# put $STARROCKS_HOME/conf ahead of $HADOOP_CLASSPATH so that custom config can replace the config in $HADOOP_CLASSPATH
export CLASSPATH=$STARROCKS_HOME/conf:$STARROCKS_HOME/lib/jni-packages/*:$HADOOP_CLASSPATH:$CLASSPATH


# ================= native section =====================
export LD_LIBRARY_PATH=$STARROCKS_HOME/lib/hadoop/native:$LD_LIBRARY_PATH
export_cachelib_lib_path


# ================== kill/start =======================
if [ ! -d $LOG_DIR ]; then
    mkdir -p $LOG_DIR
fi

if [ ! -d $UDF_RUNTIME_DIR ]; then
    mkdir -p ${UDF_RUNTIME_DIR}
fi

if [ ! -z ${UDF_RUNTIME_DIR} ]; then
    rm -f ${UDF_RUNTIME_DIR}/*
fi

if [ ${RUN_BE} -eq 1 ]; then
    pidfile=$PID_DIR/be.pid
fi
if [ ${RUN_CN} -eq 1 ]; then
    pidfile=$PID_DIR/cn.pid
fi

if [ -f $pidfile ]; then
    if kill -0 $(cat $pidfile) > /dev/null 2>&1; then
        echo "Backend running as process `cat $pidfile`. Stop it first."
        exit 1
    else
        rm $pidfile
    fi
fi

chmod 755 ${STARROCKS_HOME}/lib/starrocks_be

if [[ $(ulimit -n) -lt 60000 ]]; then
    ulimit -n 65535
fi

if [ ${RUN_BE} -eq 1 ]; then
    echo "start time: "$(date) >> $LOG_DIR/be.out
    if [ ${RUN_DAEMON} -eq 1 ]; then
        nohup ${STARROCKS_HOME}/lib/starrocks_be "$@" >> $LOG_DIR/be.out 2>&1 </dev/null &
    else
        ${STARROCKS_HOME}/lib/starrocks_be "$@" >> $LOG_DIR/be.out 2>&1 </dev/null
    fi
fi

if [ ${RUN_CN} -eq 1 ]; then
    echo "start time: "$(date) >> $LOG_DIR/cn.out
    if [ ${RUN_DAEMON} -eq 1 ]; then
        nohup ${STARROCKS_HOME}/lib/starrocks_be --cn "$@" >> $LOG_DIR/cn.out 2>&1 </dev/null &
    else
        exec ${STARROCKS_HOME}/lib/starrocks_be --cn "$@" >> $LOG_DIR/cn.out 2>&1 </dev/null
    fi
fi



