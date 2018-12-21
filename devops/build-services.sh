#!/usr/bin/env bash

echo "********************************************************"
echo "building images for services"
echo "********************************************************"

echo "********************************************************"
echo "building configuration server image"
echo "********************************************************"
cd ./../configurationserver
mvn clean package docker:build

echo "********************************************************"
echo "building eureka server image"
echo "********************************************************"
cd ./../eurekaserver/
mvn clean package docker:build

echo "********************************************************"
echo "building licensing service image"
echo "********************************************************"
cd ./../licensing/
mvn clean package docker:build

echo "********************************************************"
echo "building organization service image"
echo "********************************************************"
cd ./../organizations/
mvn clean package docker:build

echo "********************************************************"
echo "building zuul server image"
echo "********************************************************"
cd ./../zuulsvr/
mvn clean package docker:build
