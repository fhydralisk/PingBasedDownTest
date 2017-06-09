package cn.edu.tsinghua.ee.fi.cluster_shard_test

import java.io.FileWriter

import akka.actor.ActorSystem
import akka.cluster.Cluster
import com.typesafe.config.{Config, ConfigFactory}

import concurrent.duration._

/**
  * Created by hydra on 2017/3/31.
  */
object ClusterShardTest {
  val startupTime: Long = System.currentTimeMillis()
  def main(args: Array[String]): Unit = {
    val roleConfig = ConfigFactory.parseString(s"akka.cluster.roles = [${args(1)}]")

    val config = roleConfig
      .withFallback(ConfigFactory.parseString(s"akka.remote.netty.tcp.hostname = 10.0.0.${args(0)}"))
      .withFallback(ConfigFactory.parseResources("application.conf"))

    val system = startSystem(config)
    val cluster = Cluster(system)

    import system.dispatcher

    val restartingTask = system.scheduler.scheduleOnce(45 second) {
      system.terminate()
    }

    cluster.registerOnMemberUp {
      //remove restarting task here
      restartingTask.cancel()
      autoRestart(system, cluster, args(1), args(0))
    }

    print("heartbeat-pause:")
    println(cluster.settings.FailureDetectorConfig.getDuration("acceptable-heartbeat-pause"))

  }

  def startSystem(config: Config): ActorSystem = {
    val system = ActorSystem.create("ClusterShard", config)
    system.actorOf(Service.props(config.getConfig("service")), name = "service")

    system
  }

  def singletonToShutdown(system: ActorSystem): Boolean = {
    val cluster = Cluster(system)
    if (cluster.state.members.size == 1)
      true
    else
      false
  }

  def logToFile(path: String, action: String, time: Long): Unit = {
    val fw = new FileWriter(path, true)
    try fw.write(s"Action: $action; Time: $time")
    finally fw.close()
  }

  def autoRestart(system: ActorSystem, cluster: Cluster, role: String, node: String): Unit = {
    def runner: Unit = {
      if (singletonToShutdown(system)) {
        system.terminate()
        println(s"System shutdown, running time: ${(System.currentTimeMillis() - startupTime)/1000} seconds")
        logToFile(s"${System.getProperty("user.home")}/log_losstest$node.log", "Startup", startupTime)
        logToFile(s"${System.getProperty("user.home")}/log_losstest$node.log", s"Terminate, running: ${(System.currentTimeMillis() - startupTime)/1000} seconds", startupTime)
      }

      role match {
        case "leader" =>

          if (!cluster.state.members.exists( _.roles.contains("normal"))) {
            system.terminate()
            println(s"System shutdown due to normal members being kicked, running time:  ${(System.currentTimeMillis() - startupTime)/1000} seconds")
            logToFile(s"${System.getProperty("user.home")}/log_losstest$node.log", "Startup", startupTime)
            logToFile(s"${System.getProperty("user.home")}/log_losstest$node.log", s"Terminate due to kicking normal, running: ${(System.currentTimeMillis() - startupTime)/1000} seconds", startupTime)
          }

        case "normal" =>

          if (!cluster.state.members.exists( _.roles.contains("leader"))) {
            system.terminate()
            println(s"System shutdown due to leader member being kicked, running time:  ${(System.currentTimeMillis() - startupTime)/1000} seconds")
            logToFile(s"${System.getProperty("user.home")}/log_losstest$node.log", "Startup", startupTime)
            logToFile(s"${System.getProperty("user.home")}/log_losstest$node.log", s"Terminate due to kicking normal, running: ${(System.currentTimeMillis() - startupTime)/1000} seconds", startupTime)
          }

        case "failure" =>
        case _ =>

      }
    }
    system.scheduler.schedule(10 seconds, 1 second)(runner)(system.dispatcher)
  }
}

