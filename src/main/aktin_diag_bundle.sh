#! /bin/bash

# script to diagnose errors and bundle them with metadata for mail service
# maintainer:
set -euo pipefail

#detect deployment type (Docker or Debian)
if command -v docker &> /dev/null; then
    DEPLOYMENT_TYPE="Docker"
    WILDFLY_HOME="build-wildfly-1:/opt/wildfly"
    APACHE_HOME="build-httpd-1:/var/log/apache2"
    POSTGRES_HOME="build-database-1:/var/log/postgresql"
else
    DEPLOYMENT_TYPE="Debian"
    WILDFLY_HOME="/opt/wildfly"
    APACHE_HOME="/var/log/apache2"
    POSTGRES_HOME="/var/log/postgresql"
fi

# create a log folder for this diagnosis
CURRENT=$(date +%Y_%h_%d_%H%M)
readonly LOGFOLDER=$(pwd)/aktin_diag_$CURRENT
if [[ ! -d $(pwd)/aktin_diag_$CURRENT ]]; then
    mkdir $(pwd)/aktin_diag_$CURRENT
fi

# check for root privileges
if [[ $EUID -ne 0 ]]; then
   echo -e "Dieses Script muss mit root-Rechten ausgeführt werden!"
   exit 1
fi

copy() {
  local src=$1
  local dest=$2
  #check for docker
  if [[ $src == *":"* ]] && command -v docker &> /dev/null; then
    docker cp $src $dest
  else
    cp -R $src $dest
  fi
}

run_cmd() {
  local target=$1
  shift
  #check for docker
  if [[ $target == *":"* ]] && command -v docker &> /dev/null; then
    local container=${target%%:*}
    docker exec $container "$@"
  else
    "$@"
  fi
}

# copy wildfly log into log folder
copy $WILDFLY_HOME/standalone/log $LOGFOLDER/wildfly_log

# copy apache2 log into log folder
copy $APACHE_HOME $LOGFOLDER/apache_log

# copy postgresql log into log folder
copy $POSTGRES_HOME $LOGFOLDER/postgresql_log

# list deployments of wildfly
local_path=${WILDFLY_HOME#*:}
run_cmd $WILDFLY_HOME ls -l $local_path/standalone/deployments/ > $LOGFOLDER/deployments.txt

# check usage of hard drive space
df -h > $LOGFOLDER/diskspace.txt

# check running process
echo -e "+++++ WILDFLY SERVICE STATUS +++++$" > $LOGFOLDER/services.txt
if [[ $DEPLOYMENT_TYPE == "Docker" ]]; then
    docker ps -a --filter "name=${WILDFLY_HOME%%:*}" >> $LOGFOLDER/services.txt
else
  echo $(service wildfly status) >> $LOGFOLDER/services.txt
fi

echo -e "+++++ WILDFLY PS +++++" >> $LOGFOLDER/services.txt
echo $(ps -ef | grep wildfly) >> $LOGFOLDER/services.txt

echo -e "+++++ POSTGRES SERVICE STATUS +++++$" >> $LOGFOLDER/services.txt
if [[ $DEPLOYMENT_TYPE == "Docker" ]]; then
    docker ps -a --filter "name=${POSTGRES_HOME%%:*}" >> $LOGFOLDER/services.txt
else
  echo $(service postgresql status) >> $LOGFOLDER/services.txt
fi

echo -e "+++++ POSTGRES PS +++++" >> $LOGFOLDER/services.txt
echo $(ps -ef | grep postgresql) >> $LOGFOLDER/services.txt

echo -e "+++++ JAVA PS +++++" >> $LOGFOLDER/services.txt
echo $(ps -ef | grep java) >> $LOGFOLDER/services.txt

# check version numbers
echo -e "+++++ OPERATING SYSTEM +++++" > $LOGFOLDER/version.txt
echo $(hostnamectl) >> $LOGFOLDER/version.txt

echo -e "+++++ POSTGRES VERSION +++++" >> $LOGFOLDER/version.txt
run_cmd $POSTGRES_HOME psql --version >> $LOGFOLDER/version.txt
#echo $(psql --version) >> $LOGFOLDER/version.txt

echo -e "+++++ JAVA VERSION +++++" >> $LOGFOLDER/version.txt
echo $(java --version) >> $LOGFOLDER/version.txt

# check internal memory
top -b -n 1 > $LOGFOLDER/ram.txt

#collect metadate
echo -e "+++++ INSTITUTION ID +++++" >> $LOGFOLDER/metadata.txt
if [[ $DEPLOYMENT_TYPE == "Docker" ]]; then
  docker exec ${WILDFLY_HOME%%:*} grep '^local.o=' /usr/share/aktin/dev-aktin.properties | cut -d '=' -f2 >> $LOGFOLDER/metadata.txt
else
  grep '^local.o=' /usr/share/aktin/dev-aktin.properties | cut -d '=' -f2 >> $LOGFOLDER/metadata.txt
fi

echo -e "+++++ SITE ID +++++" >> $LOGFOLDER/metadata.txt
if [[ $DEPLOYMENT_TYPE == "Docker" ]]; then
  docker exec ${WILDFLY_HOME%%:*} grep '^local.ou=' /usr/share/aktin/dev-aktin.properties | cut -d '=' -f2 >> $LOGFOLDER/metadata.txt
else
  grep '^local.ou=' /usr/share/aktin/dev-aktin.properties | cut -d '=' -f2 >> $LOGFOLDER/metadata.txt
fi

echo -e "+++++ DWH VERSION +++++" >> $LOGFOLDER/metadata.txt
if [[ $DEPLOYMENT_TYPE == "Docker" ]]; then
  docker exec ${WILDFLY_HOME%%:*} grep '^local.cn=' /usr/share/aktin/dev-aktin.properties | cut -d '=' -f2 >> $LOGFOLDER/metadata.txt
else
  grep '^local.cn=' /usr/share/aktin/dev-aktin.properties | cut -d '=' -f2 >> $LOGFOLDER/metadata.txt
fi

echo -e "+++++ DEPLOYMENT TYPE +++++" >> $LOGFOLDER/metadata.txt
echo "$DEPLOYMENT_TYPE" >> $LOGFOLDER/metadata.txt

echo -e "+++++ TIMESTAMP +++++" >> $LOGFOLDER/metadata.txt
echo "$CURRENT" >> $LOGFOLDER/metadata.txt

# check permission and existence of folders
if run_cmd $WILDFLY_HOME test -d /var/lib/aktin/ ; then
    run_cmd $WILDFLY_HOME ls -ld /var/lib/aktin/ >> $LOGFOLDER/permissions.txt
        if run_cmd $WILDFLY_HOME test -d /var/lib/aktin/reports ; then
            run_cmd $WILDFLY_HOME ls -ld /var/lib/aktin/reports >> $LOGFOLDER/permissions.txt
        else
            echo -e "/var/lib/aktin/reports DOES NOT EXIST" >> $LOGFOLDER/permissions.txt
        fi
        if run_cmd $WILDFLY_HOME test -d /var/tmp/report-temp ; then
            run_cmd $WILDFLY_HOME ls -ld /var/tmp/report-temp >> $LOGFOLDER/permissions.txt
        else
           echo -e "/var/tmp/report-temp DOES NOT EXIST" >> $LOGFOLDER/permissions.txt
        fi
        if run_cmd $WILDFLY_HOME test -d /var/lib/aktin/report-archive ; then
            run_cmd $WILDFLY_HOME ls -ld /var/lib/aktin/report-archive >> $LOGFOLDER/permissions.txt
        else
            echo -e "/var/lib/aktin/report-archive DOES NOT EXIST" >> $LOGFOLDER/permissions.txt
        fi
        if run_cmd $WILDFLY_HOME test -d /var/lib/aktin/broker ; then
            run_cmd $WILDFLY_HOME ls -ld /var/lib/aktin/broker >> $LOGFOLDER/permissions.txt
        else
            echo -e "/var/lib/aktin/broker DOES NOT EXIST" >> $LOGFOLDER/permissions.txt
        fi
        if run_cmd $WILDFLY_HOME test -d /var/lib/aktin/broker-archive ; then
            run_cmd $WILDFLY_HOME ls -ld /var/lib/aktin/broker-archive >> $LOGFOLDER/permissions.txt
        else
            echo -e "/var/lib/aktin/broker-archive DOES NOT EXIST" >> $LOGFOLDER/permissions.txt
        fi
        if run_cmd $WILDFLY_HOME test -d /var/lib/aktin/import ; then
            run_cmd $WILDFLY_HOME ls -ld /var/lib/aktin/import >> $LOGFOLDER/permissions.txt
        else
            echo -e "/var/lib/aktin/import DOES NOT EXIST" >> $LOGFOLDER/permissions.txt
        fi
        if run_cmd $WILDFLY_HOME test -d /var/lib/aktin/import-scripts ; then
            run_cmd $WILDFLY_HOME ls -ld /var/lib/aktin/import-scripts >> $LOGFOLDER/permissions.txt
        else
            echo -e "/var/lib/aktin/import-scripts DOES NOT EXIST" >> $LOGFOLDER/permissions.txt
        fi
else
    echo -e "FOLDER /var/lib/aktin DOES NOT EXIST" >>  $LOGFOLDER/permissions.txt
fi

# print R environment variables
 R --vanilla --slave -e 'Sys.getenv()' > $LOGFOLDER/R_environment_vars.txt

#zip all logs and send per mail
tar -czf $LOGFOLDER/aktin_diag_$CURRENT.tar.gz --absolute-names --warning=no-file-changed $LOGFOLDER/
curl -u ondtmZILwmueOoS:aktindiag5918 -T $LOGFOLDER/aktindiag.tar.gz "https://cs.uol.de/public.php/webdav/aktindiag_$dt.tar.gz"