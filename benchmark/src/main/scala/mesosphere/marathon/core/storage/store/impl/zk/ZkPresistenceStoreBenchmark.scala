package mesosphere.marathon
package core.storage.store.impl.zk

import java.util.concurrent.TimeUnit

import akka.actor.{ ActorSystem, Scheduler }
import akka.stream.{ ActorMaterializer, Materializer }
import mesosphere.marathon.core.async.ExecutionContexts._
import mesosphere.marathon.core.base.LifecycleState
import mesosphere.marathon.storage.repository.StoredGroup
import mesosphere.marathon.storage.store.ZkStoreSerialization
import mesosphere.marathon.upgrade.DependencyGraphBenchmark
import org.apache.curator.framework.CuratorFrameworkFactory
import org.openjdk.jmh.annotations._
import org.openjdk.jmh.infra.Blackhole

import scala.concurrent.Await
import scala.concurrent.duration._

object ZkPresistenceStoreBenchmark {

  implicit lazy val system: ActorSystem = ActorSystem()
  implicit lazy val scheduler: Scheduler = system.scheduler
  implicit lazy val mat: Materializer = ActorMaterializer()

  val zkUri = "zk://localhost:2181/"
  val lifecycleState = LifecycleState.WatchingJVM
  val curator = {
    // Taken from mesosphere.marathon.integration.setup.ZookeeperServerTest.zkClient
    val client = CuratorFrameworkFactory.newClient(zkUri, NoRetryPolicy)
    client.start()
    val richClient = RichCuratorFramework(client)
    richClient.blockUntilConnected(LifecycleState.WatchingJVM)
    client
  }
  val zkStore = new ZkPersistenceStore(curator, 10.seconds)

  val rootGroup = DependencyGraphBenchmark.rootGroup
  val storedGroup = StoredGroup.apply(rootGroup)
}

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@BenchmarkMode(Array(Mode.Throughput, Mode.AverageTime))
@Fork(1)
class ZkPresistenceStoreBenchmark {
  import ZkPresistenceStoreBenchmark._
  import ZkStoreSerialization._

  @Benchmark
  def storeAndGetInMemory(hole: Blackhole): Unit = {
    Await.result(zkStore.store(storedGroup.id, storedGroup), 10.seconds)
  }

  @TearDown(Level.Trial)
  def shutdown(): Unit = {
    println("Shutting down...")
    curator.close()
    system.terminate()
    Await.ready(system.whenTerminated, 15.seconds)
  }
}
