#!/bin/sh

export CB_SERVER_VERSION="4.0.0-beta"
export CB_SERVER_FILE="couchbase-server-enterprise_${CB_SERVER_VERSION}-ubuntu14.04_amd64.deb"
export CB_SERVER_URL="http://packages.couchbase.com/releases/${CB_SERVER_VERSION}/${CB_SERVER_FILE}"

wget "${CB_SERVER_URL}"
dpkg -i ${CB_SERVER_FILE} && rm ${CB_SERVER_FILE}


mkdir -p /opt/couchbase
cd /opt/couchbase
mkdir -p var/lib/couchbase \
    var/lib/couchbase/config \
    var/lib/couchbase/data \
    var/lib/couchbase/stats \
    var/lib/couchbase/logs \
    var/lib/moxi
cd -

ulimit -n 40960        # nofile: max number of open files

# Start couchbase
echo "Starting Couchbase Server -- Web UI available at http://<ip>:8091"
/opt/couchbase/bin/couchbase-server &

sleep 5

# Initialize Node
curl -v -X POST http://127.0.0.1:8091/nodes/self/controller/settings --data-urlencode path=/opt/couchbase/var/lib/couchbase/data --data-urlencode index_path=/opt/couchbase/var/lib/couchbase/data

# Name the host
#- curl -v -X POST http://127.0.0.1:8091/node/controller/rename -d hostname=${HOSTNAME}

# Setup Services
curl -v -X POST http://127.0.0.1:8091/node/controller/setupServices --data-urlencode "services=kv,n1ql,index"

# Setup bucket memory
curl -v -X POST http://127.0.0.1:8091/pools/default -d indexMemoryQuota=256 -d memoryQuota=2048

# Setup default bucket
curl -v -X POST http://127.0.0.1:8091/pools/default/buckets -d flushEnabled=1 -d ramQuotaMB=2048 -d name="default" -d replicaIndex=0 -d evictionPolicy=valueOnly -d replicaNumber=0 -d threadsNumber=3 -d otherBucketsRamQuotaMB=0 -d authType=sasl -d saslPassword=

# Setup Administrator username and password
curl -v -X POST http://127.0.0.1:8091/settings/web --data-urlencode password="cb1234" --data-urlencode username="Administrator" -d port=SAME
sleep 5

curl -v -X POST http://127.0.0.1:8093/query/service -d statement="CREATE PRIMARY INDEX defaultidx on default USING GSI"

