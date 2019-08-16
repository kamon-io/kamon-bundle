import com.lightbend.sbt.javaagent.Modules
import sbt.Keys.resourceGenerators
import BundleKeys._
resolvers += Resolver.mavenLocal

lazy val instrumentationModules: Seq[ModuleID] = Seq(
  "io.kamon" %% "kamon-executors"         % "2.0.0",
  "io.kamon" %% "kamon-scala-future"      % "2.0.1",
  "io.kamon" %% "kamon-scalaz-future"     % "2.0.0",
  "io.kamon" %% "kamon-akka"              % "2.0.0",
  "io.kamon" %% "kamon-akka-http"         % "2.0.1",
  "io.kamon" %% "kamon-play"              % "2.0.0",
  "io.kamon" %% "kamon-jdbc"              % "2.0.1",
  "io.kamon" %% "kamon-logback"           % "2.0.0",
  "io.kamon" %% "kamon-system-metrics"    % "2.0.0" exclude("org.slf4j", "slf4j-api")
)

lazy val instrumentationModulesWithoutTwoThirteen: Seq[ModuleID] = Seq(
  "io.kamon" %% "kamon-cats-io"           % "2.0.0",
  "io.kamon" %% "kamon-twitter-future"    % "2.0.0"
)

val versionSettings = Seq(
  kamonCoreVersion := "2.0.0",
  kanelaAgentVersion := "1.0.1",
  instrumentationCommonVersion := "2.0.0"
)

val commonSettings = versionSettings ++ Seq(
  skip in publish := true,
  resolvers += Resolver.mavenLocal,
  resolvers += Resolver.bintrayRepo("kamon-io", "releases"),
  buildInfoPackage := "kamon.bundle",
  buildInfoKeys := Seq[BuildInfoKey](kanelaAgentJarName),
  kanelaAgentModule := "io.kamon" % "kanela-agent" % kanelaAgentVersion.value % "provided",
  kanelaAgentJar := update.value.matching(Modules.exactFilter(kanelaAgentModule.value)).head,
  kanelaAgentJarName := kanelaAgentJar.value.getName,
  resourceGenerators in Compile += Def.task(Seq(kanelaAgentJar.value)).taskValue,
  kamonCoreExclusion := ExclusionRule(organization = "io.kamon", name = s"kamon-core_${scalaBinaryVersion.value}"),
  bundleDependencies := Seq(
    kanelaAgentModule.value,
    "net.bytebuddy" %  "byte-buddy-agent"             % "1.9.12",
  ),
  packageBin in Compile := assembly.value,
  assembleArtifact in assemblyPackageScala := false,
  assemblyShadeRules in assembly := Seq(
    ShadeRule.zap("**module-info").inAll,
    ShadeRule.rename("net.bytebuddy.agent.**" -> "kamon.lib.@0").inAll
  ),
  assemblyMergeStrategy in assembly := {
    case "reference.conf" => MergeStrategy.concat
    case anyOther         => (assemblyMergeStrategy in assembly).value(anyOther)
  }
)

lazy val root = Project("kamon-bundle", file("."))
   .settings(noPublishing: _*)
   .disablePlugins(AssemblyPlugin)
   .aggregate(`bundle-slim`, `bundle-full`, `publishing-slim`, `publishing-full`)

lazy val bundle = (project in file("bundle"))
  .enablePlugins(BuildInfoPlugin)
  .disablePlugins(AssemblyPlugin)
  .settings(commonSettings: _*)
  .settings(
    moduleName := "kamon-bundle-core",
    libraryDependencies ++= bundleDependencies.value
  )

val `bundle-slim` = (project in file("bundle-slim"))
  .dependsOn(bundle)
  .enablePlugins(AssemblyPlugin)
  .settings(commonSettings: _*)
  .settings(
    moduleName := "kamon-bundle-slim",
    libraryDependencies ++= bundleDependencies.value
  )

val `bundle-full` = (project in file("bundle-full"))
  .dependsOn(bundle)
  .enablePlugins(AssemblyPlugin)
  .settings(commonSettings: _*)
  .settings(
    moduleName := "kamon-bundle",
    bundleDependencies ++= Seq(
      "io.kamon"      %% "kamon-status-page"            % kamonCoreVersion.value excludeAll(kamonCoreExclusion.value) changing(),
      "io.kamon"      %% "kamon-instrumentation-common" % instrumentationCommonVersion.value excludeAll(kamonCoreExclusion.value) changing(),
    ),
    libraryDependencies ++= {
      val optionalInstrumentation = if(scalaBinaryVersion.value != "2.13") instrumentationModulesWithoutTwoThirteen else Seq.empty
      val allInstrumentation = instrumentationModules ++ optionalInstrumentation
      bundleDependencies.value ++ allInstrumentation.map(_.excludeAll(kamonCoreExclusion.value))
    }
  )

lazy val `publishing-slim` = project
  .settings(versionSettings: _*)
  .settings(
    moduleName := (moduleName in (`bundle-slim`, Compile)).value,
    scalaVersion := (scalaVersion in `bundle-slim`).value,
    crossScalaVersions := (crossScalaVersions in `bundle-slim`).value,
    packageBin in Compile := (packageBin in (`bundle-slim`, Compile)).value,
    packageSrc in Compile := (packageSrc in (`bundle-slim`, Compile)).value,
    bintrayPackage := "kamon-bundle-slim",
    libraryDependencies ++= Seq(
      "io.kamon" %% "kamon-core" % kamonCoreVersion.value
    )
  )

lazy val `publishing-full` = project
  .settings(versionSettings: _*)
  .settings(
    moduleName := (moduleName in (`bundle-full`, Compile)).value,
    scalaVersion := (scalaVersion in `bundle-full`).value,
    crossScalaVersions := (crossScalaVersions in `bundle-full`).value,
    packageBin in Compile := (packageBin in (`bundle-full`, Compile)).value,
    packageSrc in Compile := (packageSrc in (`bundle-full`, Compile)).value,
    bintrayPackage := "kamon-bundle",
    libraryDependencies ++= Seq(
      "io.kamon" %% "kamon-core" % kamonCoreVersion.value
    )
  )
