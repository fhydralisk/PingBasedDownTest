package cn.edu.tsinghua.ee.fi.cluster_shard_test

import akka.actor.{Actor, Props}
import akka.cluster.Cluster
import cn.edu.tsinghua.ee.fi.pingbaseddown.PingBasedDownNotification.{ClusterFailure, NodeFailure}
import com.typesafe.config.Config

import concurrent.duration._

/**
  * Created by hydra on 2017/4/13.
  */

object Service {
  def props(config: Config): Props = Props(new Service(config))

  object Messages {
    case class ServiceRequest()
    case class ServiceResponse()
  }
}

class Service(config: Config) extends Actor {
  val cluster = Cluster(context.system)

  import context.dispatcher

  private val broadcastAfter = config.getDuration("start-broadcast-after")
  private val broadcastInterval = config.getDuration("broadcast-interval")
  private val broadcastQuantity = config.getInt("broadcast-quantity")

  if (!broadcastAfter.isNegative)
    context.system.scheduler.schedule(Duration.fromNanos(broadcastAfter.toNanos), Duration.fromNanos(broadcastInterval.toNanos)) {
      cluster.state.members.filterNot (_.address == cluster.selfAddress) foreach { m =>
        1 to broadcastQuantity foreach { _ =>
          context.actorSelection(s"${m.address.protocol}://${m.address.hostPort}/user/service") ! Service.Messages.ServiceRequest()
        }
      }
    }

  override def receive = {
    case Service.Messages.ServiceRequest() =>
      sender() ! Service.Messages.ServiceResponse()

    case NodeFailure =>
      System.exit(0)

    case ClusterFailure =>
      System.exit(0)

    case _ =>

  }
}
