package org.tmt.tel.plcprototypedeploy

import csw.framework.deploy.containercmd.ContainerCmd

object PlcprototypeContainerCmdApp extends App {

  ContainerCmd.start("plc-prototype-container-cmd-app", args)

}
