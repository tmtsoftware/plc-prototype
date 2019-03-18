# plc-prototype

This project implements an HCD (Hardware Control Daemon) and an Assembly using 
TMT Common Software ([CSW](https://github.com/tmtsoftware/csw)) APIs.   The HCD
communicates with a PLC, provides configurable telemetry and supports external
read and write commands.

## Subprojects

* plc-prototype-assembly - an assembly that talks to the plc-prototype HCD
* plc-prototype-hcd - an HCD that talks to the plc-prototype hardware
* plc-prototype-deploy - for starting/deploying HCDs and assemblies

## Build Instructions

The build is based on sbt and depends on libraries generated from the 
[csw](https://github.com/tmtsoftware/csw) project, and the javaplc library.

To include the javaplc library in the build, clone the project and run: sbt publishLocal

See [here](https://www.scala-sbt.org/1.0/docs/Setup.html) for instructions on installing sbt.

## Building the HCD and Assembly Applications

 run `sbt stage`, which installs the applications locally in ./target/universal/stage/bin.


## Prerequisites for running Components

The CSW services need to be running before starting the components. 
This is done by starting the `csw-services.sh` script, which is installed as part of the csw build.
If you are not building csw from the sources, you can get the script as follows:

 - Download csw-apps zip from https://github.com/tmtsoftware/csw/releases.
 - Unzip the downloaded zip.
 - Go to the bin directory where you will find `csw-services.sh` script.
 - Run `./csw_services.sh --help` to get more information.
 - Run `./csw_services.sh start` to start the location service and config server.


## Setup initial PLC configuration

The provided example configuration works the the PLC in the TMT project office lab.  This configuration will need to be
stored into the configuration service.   A script has been provided for this:

cd plc-prototype-deploy/src/main/resources

./initialize-config.sh <IP address of configuration service>

## Running the HCD and Assembly

cd plc-prototype-deploy/target/universal/stage/bin

./plcprototype-container-cmd-app --local ../../../../src/main/resources/JPlcprototypeContainer.conf

## Sending commands from the client program

cd plc-prototype-deploy/target/universal/stage/bin

./command-client


```
