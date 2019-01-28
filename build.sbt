lazy val aggregatedProjects: Seq[ProjectReference] = Seq(
  `plc-prototype-assembly`,
  `plc-prototype-hcd`,
  `plc-prototype-client`,
  `plc-prototype-deploy`
)

lazy val `plc-prototype` = project
  .in(file("."))
  .aggregate(aggregatedProjects: _*)

lazy val `plc-prototype-assembly` = project
  .settings(
    libraryDependencies ++= Dependencies.PlcprototypeAssembly
  )

lazy val `plc-prototype-hcd` = project
  .settings(
    libraryDependencies ++= Dependencies.PlcprototypeHcd
  )

lazy val `plc-prototype-client` = project
  .settings(
    libraryDependencies ++= Dependencies.PlcprototypeClient
  )

lazy val `plc-prototype-deploy` = project
  .dependsOn(
    `plc-prototype-assembly`,
    `plc-prototype-hcd`,
    `plc-prototype-client`
  )
  .enablePlugins(JavaAppPackaging, CswBuildInfo)
  .settings(
    libraryDependencies ++= Dependencies.PlcprototypeDeploy
  )
