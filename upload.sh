#!/usr/bin/env bash
# $1 local file path
# $2 remote file path
# $3 credentials as user:password
curl -T $1 -u $3 https://delicias.dia.fi.upm.es/nextcloud/remote.php/webdav/$2
