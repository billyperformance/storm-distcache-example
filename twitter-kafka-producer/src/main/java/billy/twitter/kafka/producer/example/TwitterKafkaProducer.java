package billy.twitter.kafka.producer.example;

import com.google.common.collect.Lists;
import com.twitter.hbc.ClientBuilder;
import com.twitter.hbc.core.Client;
import com.twitter.hbc.core.Constants;
import com.twitter.hbc.core.endpoint.StatusesFilterEndpoint;
import com.twitter.hbc.core.processor.StringDelimitedProcessor;
import com.twitter.hbc.httpclient.auth.Authentication;
import com.twitter.hbc.httpclient.auth.OAuth1;
import kafka.javaapi.producer.Producer;
import kafka.producer.KeyedMessage;
import kafka.producer.ProducerConfig;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import java.io.InputStream;
import java.util.Properties;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static jdk.nashorn.internal.objects.Global.println;
import static jdk.nashorn.internal.runtime.regexp.joni.Config.log;
import static sun.management.Agent.error;

public class TwitterKafkaProducer {

	private static final Integer MAX_MSG_TO_SEND = 20000;
	private static final String TOPIC = "twitter-topic";

	public static void run(String consumerKey, String consumerSecret, String token, String secret, String trackTerms) throws InterruptedException {

		Properties properties = new Properties();
		properties.put("metadata.broker.list", "localhost:9092");
		properties.put("serializer.class", "kafka.serializer.StringEncoder");
		properties.put("client.id","storm-distcache-topology");
		ProducerConfig producerConfig = new ProducerConfig(properties);
		Producer<String, String> producer = new Producer<String, String>(producerConfig);

		BlockingQueue<String> queue = new LinkedBlockingQueue<>(10000);
		StatusesFilterEndpoint endpoint = new StatusesFilterEndpoint();
		// add some track terms
		endpoint.trackTerms(Lists.newArrayList("twitterapi", trackTerms));

		Authentication auth = new OAuth1(consumerKey, consumerSecret, token, secret);

		// Create a new BasicClient. By default gzip is enabled.
		Client client = new ClientBuilder().hosts(Constants.STREAM_HOST)
				.endpoint(endpoint).authentication(auth)
				.processor(new StringDelimitedProcessor(queue)).build();

		// Establish a connection
		client.connect();

		// Push Tweets to Kafka through the producer
		for (int msgRead = 0; msgRead < MAX_MSG_TO_SEND; msgRead++) {
			KeyedMessage<String, String> message = null;
			try {
				message = new KeyedMessage<>(TOPIC, queue.take());
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			producer.send(message);
		}
		producer.close();

		client.stop();
	}

	public static void main(String[] args) {

		Properties props = loadExternalProperties();

		String consumerKey = props.getProperty("consumer.key");
		String consumerSecret = props.getProperty("consumer.secret");
		String token = props.getProperty("token");
		String tokenSecret = props.getProperty("token.secret");

		try {
            TwitterKafkaProducer.run(consumerKey, consumerSecret, token, tokenSecret, args[0]);
        } catch (InterruptedException e) {
			System.err.println(e);
		}
	}

	private static Properties loadExternalProperties() {
		Properties props = new Properties();
		try {
			InputStream stream = TwitterKafkaProducer.class.getClassLoader().getResourceAsStream("twitter.properties");
			props.load(stream);
		} catch (Exception e) {
			System.err.println("Error loading Twitter kafka producer properties: " + e.getLocalizedMessage());
		}
		return props;
	}
}

