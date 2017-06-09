package cn.edu.tsinghua.ee.fi.cluster_shard_test

import akka.actor.{Actor, Props}
import akka.cluster.Cluster

import concurrent.duration._

/**
  * Created by hydra on 2017/4/13.
  */

object Service {
  def props: Props = Props(classOf[Service])

  object Messages {
    case class ServiceRequest()
    case class ServiceResponse()
  }
}

class Service extends Actor {
  val cluster = Cluster(context.system)

  import context.dispatcher

  context.system.scheduler.schedule(1 second, 10 millis) {
    cluster.state.members.filterNot (_.address == cluster.selfAddress) foreach { m =>
      context.actorSelection(s"${m.address.protocol}://${m.address.hostPort}/user/service") ! Service.Messages.ServiceRequest()
    }
  }

  override def receive = {
    case Service.Messages.ServiceRequest() =>
      sender() ! Service.Messages.ServiceResponse()
    case _ =>

  }
}
