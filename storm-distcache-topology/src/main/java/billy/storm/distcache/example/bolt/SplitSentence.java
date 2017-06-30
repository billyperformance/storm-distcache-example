package billy.storm.distcache.example.bolt;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.BasicOutputCollector;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.base.BaseBasicBolt;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SplitSentence extends BaseBasicBolt {

  private static final Logger logger = LogManager.getLogger(SplitSentence.class);
  private com.google.gson.JsonParser parser;
  private String componentId;

  @Override
  public void prepare(Map stormConf, TopologyContext context) {
    this.componentId = context.getThisComponentId();
    this.parser = new com.google.gson.JsonParser();
  }

  @Override
  public void execute(Tuple tuple, BasicOutputCollector collector) {
    String sentence = tuple.getString(0);

    try {
      JsonObject jsonMsg = parser.parse(sentence).getAsJsonObject();
      JsonElement tweetTextJSONElement = jsonMsg.get("text");
      String tweet = tweetTextJSONElement.toString();

      //logger.info("");
      //logger.info("["+componentId+"] Tweet -> "+tweet);

      List<String> cleanTweet = prepareTweet(tweet);
      //logger.info("["+componentId+"] * Tweet words list: ");

      for( String word: cleanTweet){
        //logger.info("["+componentId+"]  - "+word);
        collector.emit(new Values(word));
      }
    } catch (Exception e){
      //Skip malformed message
    }
  }

  @Override
  public void declareOutputFields(OutputFieldsDeclarer declarer) {
    declarer.declare(new Fields("word"));
  }

  private List<String> prepareTweet(String tweet) {

    List<String> wordsList = new ArrayList<>();

    // Lower case & remove URL, line breaks, (") symbols and backslashes
    String lowerCasedTweetWithoutURL = removeUrl(tweet.toLowerCase().replaceAll("\n", "").replaceAll("\"", "").replaceAll("\\\\", "\\\\\\\\"));

    //Tokenize tweet
    StringTokenizer st = new StringTokenizer(lowerCasedTweetWithoutURL);

    while (st.hasMoreElements()) {
      String word = (String) st.nextElement();
      //Remove empty words, and symbols of Retweet, # and @
      if(!word.equals("") && !word.equals("rt") && !word.startsWith("#") && !word.startsWith("@")) {
        wordsList.add(word);
      }
    }
    return wordsList;
  }

  private String removeUrl(String str) {
    String urlPattern = "((https?|ftp|gopher|telnet|file|Unsure|http):((//)|(\\\\))+[\\w\\d:#@%/;$()~_?\\+-=\\\\\\.&]*)";
    Pattern p = Pattern.compile(urlPattern,Pattern.CASE_INSENSITIVE);
    Matcher m = p.matcher(str);
    int i = 0;
    while (m.find()) {
      str = str.replaceAll(m.group(i),"").trim();
      i++;
    }
    return str;
  }

}

