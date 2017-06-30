# Storm distributed cache example

This projet is a simple example to run a **Twitter WordCount** using an [Apache Storm](http://storm.apache.org) topology by tracking a set of specific file called *wordsToTrack* stored and distributed within an Apache Storm cluster exploiting the distributed BLOB storage capability.

## Requirements

* Java Oracle JDK 1.8 or similar
* Maven
* Docker

## Overview

The project includes an infrastructure sub-project which helps to spin a [Dockerized](https://www.docker.com) environment ([Zookeeper](https://zookeeper.apache.org), [Storm](http://storm.apache.org) and [Kafka](https://kafka.apache.org)) to run the example.

```bash
├── README.md
├── infrastructure
├── storm-distcache-topology
└── twitter-kafka-producer
```

## Infrastructure

The easiest way to setup the example environment is by running:

```bash
$ cd storm-distcache-example/infrastructure/
$ ./start.sh
```

The *start.sh* script fires up a Docker compose which builds the necessary infrastructure. A total of five containers should be *up & running* up to this point.

```bash
docker ps

CONTAINER ID        IMAGE                COMMAND                  CREATED   STATUS              PORTS                      NAMES
9c3a98f84200        storm:1.0.3          "/docker-entrypoin..."   ...        ...                                         supervisor
55a2fa805ef5        storm:1.0.3          "/docker-entrypoin..."   ...        ...          0.0.0.0:8080->8080/tcp         ui
284a26fd5d14        storm:1.0.3          "/docker-entrypoin..."   ...        ...          0.0.0.0:6627->6627/tcp         nimbus
9dcfa5ad74cc        wurstmeister/kafka   "start-kafka.sh"         ...        ...          0.0.0.0:9092->9092/tcp         kafka
e6b2f10dd6cf        zookeeper            "/docker-entrypoin..."   ...        ...          2181/tcp, 2888/tcp, 3888/tcp   zookeeper
```

## Running the example

### The Twitter Kafka producer

The Twitter Kafka producer uses [Hosebird Client (hbc)](https://github.com/twitter/hbc) for consuming Twitter's Streaming API and uses the [Track](https://dev.twitter.com/streaming/overview/request-parameters#track) operation accepting a comma-separated list of phrases which will be used to determine what Tweets will be delivered on the stream.

Before running the producer we should register a ***New App*** under the https://apps.twitter.com service in order to get the developer credentials: ***Consumer Key***, ***Consumer Secret***, ***Access Token*** and ***Access Token Secret***. Then you should edit the initial credentials as follows:

```bash
$ cd ../twitter-kafka-producer/
$ vi src/main/resources/twitter.properties
```

```java
consumer.key=EFty7GUjzSu72AJweLB3e3dnMrR
consumer.secret=HbvE9D0ITxgKqVhjhjGGfddH7c7oUp8J1eGw4jSy5f1RZkK
token=2943654249-e1ltKPiOvVtyyuJMytGugLG4D973vlMQSh
token.secret=qlXLbnxTbeBaBjjjGGtFGHjJJ5tKEdfK32EFb8
```

Now it's time to build the producer:

```bash
$ mvn clean package
```

And run it:

```bash
$ java -jar target/twitter-kafka-producer-1.0.0-SNAPSHOT.jar <word to track>
```

Example:

```bash
$ java -jar target/twitter-kafka-producer-1.0.0-SNAPSHOT.jar trump
```

Automatically the Twitter Kafka producer will start to inject Tweets into a Kafka topic named *twitter-topic*.

### The Storm Distributed Cache

The Storm [distributed cache](http://storm.apache.org/releases/1.1.0/distcache-blobstore.html) feature is used to efficiently distribute files (or blobs) that are large and can change during the lifetime of a topology, such as geo-location data, dictionaries, etc. Typical use cases include phrase recognition, entity extraction, document classification and so forth.

At the starting time of a topology, the user specifies the set of files the topology needs. Once a topology is running, the user at any time can request for any file in the distributed cache to be updated with a newer version. The updating of blobs happens in an eventual consistency model.

Continuing with the example we will edit the initial list of words to track at convenience as follows:

```bash
$ cd ../storm-distcache-topology/
$ vi src/main/resources/wordsToTrack.list
```

Then we will upload this file into the cluster:

```bash
$ docker ps | grep nimbus | awk '{print$1}' #i.e: 284a26fd5d14
$ docker cp src/main/resources/wordsToTrack.list 284a26fd5d14:wordsToTrack.list
$ docker exec -t -i 284a26fd5d14 /bin/bash
```

Once logged inside the *Nimbus* container, we upload the *wordsToTrack.list* tagged as ***wordstotrack*** key:

```bash
$ cd /
$ storm blobstore create --file wordsToTrack.list --acl o::rwa wordstotrack
```

Check the file consistency directly into the blobstore:

```bash
$ storm blobstore list wordstotrack
  NimbusClient - Found leader nimbus : 284a26fd5d14:6627
  blobstore - wordstotrack 1498748509000 ("o::rwa")
```
Or by running a `cat` operation:

```bash
$ storm blobstore cat wordstotrack
  NimbusClient - Found leader nimbus : 284a26fd5d14:6627
    win
    stupid
    weak
    loser
    ...
```

### The Twitter WordCount topology

The *Twitter WordCount topology* is based on the classic [Storm WordCount](http://www.corejavaguru.com/bigdata/storm/word-count-topology) example.

This topology will read Tweets from the Kafka topic (twitter-topic) and, perform some text-cleaning operation, split the text in words and count every occurence of the words listed in the initial *wordsToTrack.list* file.

In order to build the topology we will run:

```bash
$ cd storm-distcache-topology/
$ mvn clean package
```

Then it's time to upload the resulting artifact to the *Nimbus* container by:

```bash
$ docker cp target/storm-distcache-topology-1.0.0-SNAPSHOT.jar 284a26fd5d14:storm-distcache-topology-1.0.0-SNAPSHOT.jar
$ docker exec -t -i 284a26fd5d14 /bin/bash
$ ls -la /storm-*
     1 501      dialout   19149093 Jun 29 15:09 /storm-distcache-topology-1.0.0-SNAPSHOT.jar
```

And start the topology:

```bash
$ cd /
$ storm jar storm-distcache-topology-1.0.0-SNAPSHOT.jar billy.storm.distcache.example.WordCountTopology
```

Once the topology is *up & running* it will automatically start counting the word ocurrences contained into the *wordstotrack* blob file from the incoming Tweets. It will also print the file content when this is loaded for the first time:

```bash
WordCount - [count] Loading BLOB contents (wordstotrack v1498748509000):
WordCount -   - win
WordCount -   - stupid
WordCount -   - weak
WordCount -   - loser
...
```

Example of topology output:

```bash
WordCount - [count] Count summary:
WordCount - [count]    * Word "terrific" appears 1 times in 251111 messages (0 % total)
WordCount - [count]    * Word "obama" appears 528 times in 251111 messages (0.21 % total)
WordCount - [count]    * Word "dangerous" appears 11 times in 251111 messages (0 % total)
WordCount - [count]    * Word "mexicans" appears 13 times in 251111 messages (0.01 % total)
WordCount - [count]    * Word "tremendous" appears 3 times in 251111 messages (0 % total)
WordCount - [count]    * Word "disaster" appears 1 times in 251111 messages (0 % total)
```

The next step will be to update (reload) the *wordstotrack* BLOB file without re-deploying the whole topology. The distributed cache will take care of propagate the updates through the cluster nodes.

We will edit the *wordsToTrack.list* file and add or remove some words to track, and refresh it:

```bash
$ vi /wordsToTrack.list
$ storm blobstore update -f /wordsToTrack.list wordstotrack
```
We sould check see the topology reloading the file from blob storage (note: We removed the `win` word)

```bash
WordCount - [count] Loading BLOB contents (wordstotrack v1498754196000):
WordCount -   - stupid
WordCount -   - weak
WordCount -   - loser
...
```

As we could see the topology only updates the file when the file version changes:

```bash
WordCount - [count] Loading BLOB contents (wordstotrack v1498748509000) # Initial file
WordCount - [count] Loading BLOB contents (wordstotrack v1498754196000) # Reload after the update
```

Finally, we can shutdown the example environment by running the following commands into the project directory:

```bash
$ cd infrastructure/
$ ./stop.sh
...
```

