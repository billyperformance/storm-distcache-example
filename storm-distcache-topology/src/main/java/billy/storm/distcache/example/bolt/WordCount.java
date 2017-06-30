package billy.storm.distcache.example.bolt;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.storm.Config;
import org.apache.storm.Constants;
import org.apache.storm.blobstore.ClientBlobStore;
import org.apache.storm.blobstore.InputStreamWithMeta;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.BasicOutputCollector;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.base.BaseBasicBolt;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;
import org.apache.storm.utils.Utils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class WordCount extends BaseBasicBolt {

  private static final Logger logger = LogManager.getLogger(WordCount.class);
  private static final String BLOB_NAME = "wordstotrack";

  private String componentId;
  private ClientBlobStore clientBlobStore;

  private Integer tickCounter;
  private Map<String, Integer> counts;
  private Integer emitFrequency;
  private Set<String> wordsToTrack;
    private Long wordsToTrackBlobVersion = 0L;
    private Integer msgCounter;


  public WordCount(Integer frequency) {
    this.emitFrequency = frequency;
  }

  public void prepare(Map conf, TopologyContext context) {
    this.componentId = context.getThisComponentId();
    this.clientBlobStore = Utils.getClientBlobStore(conf);
    this.wordsToTrack = readBlobFromDistCache(clientBlobStore, BLOB_NAME).getWordsToTrack();
    this.counts = new HashMap<>();
    this.msgCounter = 0;
    this.tickCounter = 0;
  }

  @Override
  public Map<String, Object> getComponentConfiguration() {
      Config conf = new Config();
      conf.put(Config.TOPOLOGY_TICK_TUPLE_FREQ_SECS, emitFrequency);
      return conf;
  }

  @Override
  public void execute(Tuple tuple, BasicOutputCollector collector) {

    //Increase the message counter
    this.msgCounter++;

    //If it's a tick tuple, emit all words and counts
    if(isTickTuple(tuple)) {
        //Update BLOB contents every 2 ticks
        tickCounter++;
        if((tickCounter % 2) == 0) {
          //Reloading wordsToTrack map
            ReadBlobFromDistCacheResponse blobResponse = readBlobFromDistCache(clientBlobStore, BLOB_NAME);
            if(blobResponse.getUpdated()) {
                this.wordsToTrack = readBlobFromDistCache(clientBlobStore, BLOB_NAME).getWordsToTrack();
            }
            tickCounter = 0;
        }

        logger.info("["+componentId+"] Count summary: ");
        //Emit all the words and counters
        for(String word : counts.keySet()) {
          Integer count = counts.get(word);
          collector.emit(new Values(word, count));
          if(msgCounter > 0) {
              Double ocurrencePercentage = ((double) count / msgCounter) * 100;
              logger.info("[" + componentId + "]    * Word \"" + word + "\" appears " + count + " times in " + msgCounter + " messages (" +  new DecimalFormat("#.##").format(ocurrencePercentage) + " % total)");
          }
        }
        logger.info("");
    } else {
      //Get the word contents from the tuple
      String word = tuple.getString(0);

      if(wordsToTrack.contains(word)) {
        //Have we counted any already?
        Integer count = counts.get(word);
        if (count == null)
          count = 0;
        //Increment the count and store it
        // only if it's on words to track set
        count++;
        counts.put(word, count);
      }
    }
  }

  //Declare that we will emit a tuple containing two fields; word and count
  @Override
  public void declareOutputFields(OutputFieldsDeclarer declarer) {
    declarer.declare(new Fields("word", "count"));
  }

  private ReadBlobFromDistCacheResponse readBlobFromDistCache(ClientBlobStore clientBlobStore, String blobName) {
    Set<String> wordsToTrack = new HashSet<>();
      Boolean isNewBlobVersion = false;

      try {
          InputStreamWithMeta blobInputStream = clientBlobStore.getBlob(blobName);
          isNewBlobVersion = isNewBlobVersionAvailable(clientBlobStore, blobName);

          if(isNewBlobVersion) {
              this.wordsToTrackBlobVersion = blobInputStream.getVersion();
              BufferedReader r = new BufferedReader(new InputStreamReader(blobInputStream));
              String blobLine;
              logger.info("["+componentId+"] Loading BLOB contents ("+blobName+" v"+blobInputStream.getVersion()+"): ");
              try {
                  while ((blobLine = r.readLine()) != null) {
                      logger.info("  - "+blobLine);
                      wordsToTrack.add(blobLine);
                  }
              } catch(Exception e) {
                  e.printStackTrace();
              }
              logger.info("");
          }
    } catch (Exception e) {
      logger.error("["+componentId+"] Error reading words to track file", e);
    }

    return new ReadBlobFromDistCacheResponse(isNewBlobVersion, wordsToTrack);
  }

  private static boolean isTickTuple(Tuple tuple) {
    return tuple.getSourceComponent().equals(Constants.SYSTEM_COMPONENT_ID)
            && tuple.getSourceStreamId().equals(Constants.SYSTEM_TICK_STREAM_ID);
  }

  private Boolean isNewBlobVersionAvailable(ClientBlobStore clientBlobStore, String blobName) {
      InputStreamWithMeta blobInputStream;
      Boolean ret = false;
      try {
          blobInputStream = clientBlobStore.getBlob(blobName);
          ret = this.wordsToTrackBlobVersion != blobInputStream.getVersion();
      } catch(Exception e){
          logger.error("["+componentId+"] Error reading BLOB file: "+blobName, e);
      }
      return ret;
  }

  private class ReadBlobFromDistCacheResponse {

      private Boolean updated;
      private Set<String> wordsToTrack;

      public ReadBlobFromDistCacheResponse(Boolean updated, Set<String> wordsToTrack) {
          this.updated = updated;
          this.wordsToTrack = wordsToTrack;
      }

      public Boolean getUpdated() {
          return updated;
      }

      public Set<String> getWordsToTrack() {
          return wordsToTrack;
      }

      @Override
      public String toString() {
          return "ReadBlobFromDistCacheResponse{" +
                  "updated=" + updated +
                  ", wordsToTrack=" + wordsToTrack +
                  '}';
      }
  }


}

