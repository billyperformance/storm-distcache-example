package billy.storm.distcache.example;

import billy.storm.distcache.example.bolt.SplitSentence;
import billy.storm.distcache.example.bolt.WordCount;
import kafka.api.OffsetRequest;
import org.apache.storm.Config;
import org.apache.storm.LocalCluster;
import org.apache.storm.StormSubmitter;
import org.apache.storm.kafka.KafkaSpout;
import org.apache.storm.kafka.SpoutConfig;
import org.apache.storm.kafka.StringScheme;
import org.apache.storm.kafka.ZkHosts;
import org.apache.storm.spout.SchemeAsMultiScheme;
import org.apache.storm.topology.TopologyBuilder;
import org.apache.storm.tuple.Fields;

import java.util.ArrayList;
import java.util.List;

public class WordCountTopology {

  public static final Integer SECONDS_TO_RUN_IN_LOCAL = 1200; //In seconds
  public static final Integer UPDATE_RATE = 10; //In seconds

  public static final String TOPOLOGY_NAME = "word-count";
  public static final String KAFKA_TOPIC_NAME = "twitter-topic";
  public static final String ZK_SERVERS = "zookeeper:2181";
  public static final String ZK_PATH = "/storm-kafka/";

  //Entry point for the topology
  public static void main(String[] args) throws Exception {

    //Used to build the topology
    TopologyBuilder builder = new TopologyBuilder();

    //New configuration
    Config conf = new Config();
    conf.setDebug(false);

    builder.setSpout("kafka-spout", new KafkaSpout(getSpoutConfig(TOPOLOGY_NAME, KAFKA_TOPIC_NAME, false)), 1);

    builder.setBolt("split-sentence", new SplitSentence(), 8).shuffleGrouping("kafka-spout");

    //Fieldsgrouping subscribes to the split bolt, and ensures that the same word is sent to the same instance (group by field 'word')
    builder.setBolt("count", new WordCount(UPDATE_RATE), 12).fieldsGrouping("split-sentence", new Fields("word"));

    //If there are arguments, we are running on a cluster
    if (args != null && args.length > 0) {
      //parallelism hint to set the number of workers
      conf.setNumWorkers(3);
      //submit the topology
      StormSubmitter.submitTopology(args[0], conf, builder.createTopology());
    }
    //Otherwise, we are running locally
    else {
      //Cap the maximum number of executors that can be spawned for a component to 3
      conf.setMaxTaskParallelism(1);
      //LocalCluster is used to run locally
      LocalCluster cluster = new LocalCluster();
      cluster.submitTopology(TOPOLOGY_NAME, conf, builder.createTopology());
      Thread.sleep(SECONDS_TO_RUN_IN_LOCAL * 1000);
      cluster.shutdown();
    }
  }

  private static SpoutConfig getSpoutConfig(String topology, String topic, boolean earliest) {

    SpoutConfig config = new SpoutConfig(
            new ZkHosts(ZK_SERVERS), topic,
            ZK_PATH + topology, // new spout offsets location
            topic
    );

    config.startOffsetTime = earliest ? OffsetRequest.EarliestTime() : OffsetRequest.LatestTime();

    List<String> zkServers = new ArrayList<>();
    String[] zkServersParts = ZK_SERVERS.split(",");
    for (String zkHostWithPort : zkServersParts) {
      String[] parts = zkHostWithPort.split(":");
      if (parts.length > 0) {
        String zkHost = parts[0];
        zkServers.add(zkHost);
      }
    }
    config.zkServers = zkServers;
    config.zkPort = 2181;

    //Setting string schema from Kafka
    config.scheme = new SchemeAsMultiScheme(new StringScheme());

    return config;
  }
}

