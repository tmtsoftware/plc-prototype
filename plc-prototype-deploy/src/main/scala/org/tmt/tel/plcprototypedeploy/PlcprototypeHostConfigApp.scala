package org.tmt.tel.plcprototypedeploy

import csw.framework.deploy.hostconfig.HostConfig

object PlcprototypeHostConfigApp extends App {

  HostConfig.start("plc-prototype-host-config-app", args)

}
