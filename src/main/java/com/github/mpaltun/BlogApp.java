package com.github.mpaltun;

import static akka.pattern.Patterns.ask;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.github.mpaltun.author.AuthorListing;
import com.github.mpaltun.bot.Bot;
import com.github.mpaltun.post.Post;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.actor.ActorIdentity;
import akka.actor.ActorPath;
import akka.actor.ActorPaths;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Identify;
import akka.actor.Props;
import akka.cluster.sharding.ClusterSharding;
import akka.cluster.sharding.ClusterShardingSettings;
import akka.dispatch.OnFailure;
import akka.dispatch.OnSuccess;
import akka.persistence.journal.leveldb.SharedLeveldbJournal;
import akka.persistence.journal.leveldb.SharedLeveldbStore;
import akka.util.Timeout;
import scala.concurrent.Future;

public class BlogApp {

    private final String port;

    private BlogApp(String port) {
        this.port = port;
    }

    public static void main(String[] args) throws InterruptedException {
        BlogApp app = new BlogApp(args[0]);
        app.run();
    }

    private void startupSharedJournal(ActorSystem system, boolean startStore, ActorPath path) {
        // Start the shared journal one one node (don't crash this SPOF)
        // This will not be needed with a distributed journal
        if (startStore) {
            system.actorOf(Props.create(SharedLeveldbStore.class), "store");
        }
        // register the shared journal
        Future<Object> f = ask(system.actorSelection(path), new Identify(null), Timeout.apply(15, SECONDS));
        f.onSuccess(new OnSuccess<Object>() {
            @Override
            public void onSuccess(Object o) throws Throwable {
                if (o instanceof ActorIdentity) {
                    ActorIdentity identity = (ActorIdentity) o;
                    SharedLeveldbJournal.setStore(identity.getRef(), system);
                }
                else {
                    system.log().error("Shared journal not started at {}", path);
                    system.terminate();
                }
            }
        }, system.dispatcher());

        f.onFailure(new OnFailure() {
            @Override
            public void onFailure(Throwable throwable) throws Throwable {
                system.log().error("Lookup of shared journal at {} timed out", path);
                system.terminate();
            }
        }, system.dispatcher());

    }

    private void run() {
        Config config = ConfigFactory.parseString("akka.remote.netty.tcp.port=" + port)
                                     .withFallback(ConfigFactory.load());

        String clusterName = config.getString("clustering.cluster.name");
        final ActorSystem system = ActorSystem.create(clusterName, config);

        String actorPath = "akka.tcp://" + clusterName + "@" + config.getString("clustering.ip") + ":2551/user/store";
        startupSharedJournal(system, "2551".equals(port), ActorPaths.fromString(actorPath));

        ActorRef authorListing = ClusterSharding.get(system)
                                                .start(AuthorListing.SHARD,
                                                       AuthorListing.props(),
                                                       ClusterShardingSettings.create(system),
                                                       AuthorListing.shardExtractor()
                                                );

        ClusterSharding.get(system)
                       .start(Post.SHARD,
                              Post.props(authorListing),
                              ClusterShardingSettings.create(system),
                              Post.shardExtractor()
                       );

        if (!"2551".equals(port) && !"2552".equals(port)) {
            system.actorOf(Props.create(Bot.class), "bot");
        }
    }

}
