#!/bin/bash

HOST_IP=`ifconfig en0 | awk '/inet / {print$2}'`
echo HOST_IP=$HOST_IP > .env
