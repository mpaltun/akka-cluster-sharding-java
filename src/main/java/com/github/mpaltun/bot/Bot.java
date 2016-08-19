package com.github.mpaltun.bot;

import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.joining;

import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.mpaltun.author.AuthorListing;
import com.github.mpaltun.author.ImmutableGetPosts;
import com.github.mpaltun.author.PostSummary;
import com.github.mpaltun.post.Post;
import com.github.mpaltun.post.command.ImmutableChangeBody;
import com.github.mpaltun.post.command.ImmutableCreatePost;
import com.github.mpaltun.post.command.ImmutablePostContent;
import com.github.mpaltun.post.command.ImmutablePublishPost;
import com.github.mpaltun.post.command.PostCommand;
import com.google.common.collect.ImmutableMap;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Cancellable;
import akka.cluster.Cluster;
import akka.cluster.sharding.ClusterSharding;
import akka.japi.pf.ReceiveBuilder;
import scala.PartialFunction;
import scala.concurrent.duration.Duration;
import scala.runtime.BoxedUnit;

public class Bot extends AbstractActor {

    private static final Logger logger = LoggerFactory.getLogger(Bot.class);

    private final ActorRef postRegion;
    private final ActorRef listingsRegion;
    private final String from;
    private final Cancellable tickTask;

    private int n;
    private Map<Integer, String> authors = ImmutableMap.<Integer, String>builder().put(0, "Patrik")
                                                                                  .put(1, "Martin")
                                                                                  .put(2, "Roland")
                                                                                  .put(3, "Björn")
                                                                                  .put(4, "Endre")
                                                                                  .build();

    public Bot() {

        tickTask = context().system()
                            .scheduler()
                            .schedule(Duration.create(3, SECONDS),
                                      Duration.create(3, SECONDS),
                                      self(),
                                      Tick.INSTANCE,
                                      context().dispatcher(),
                                      null
                            );

        postRegion = ClusterSharding.get(context().system()).shardRegion(Post.SHARD);
        listingsRegion = ClusterSharding.get(context().system()).shardRegion(AuthorListing.SHARD);

        from = Cluster.get(context().system()).selfAddress().hostPort();
    }

    @Override
    public void postStop() throws Exception {
        super.postStop();
        tickTask.cancel();
    }

    @Override
    public PartialFunction<Object, BoxedUnit> receive() {
        return create();
    }

    private String currentAuthor() {
        return authors.get(n % authors.size());
    }

    private PartialFunction<Object, BoxedUnit> create() {
        return ReceiveBuilder.match(Tick.class, tick -> {
            String postId = UUID.randomUUID().toString();
            n++;

            String title = String.format("Post %d from %s", n, from);
            PostCommand addPost = ImmutableCreatePost.builder()
                                                     .postId(postId)
                                                     .content(ImmutablePostContent.of(currentAuthor(), title, "..."))
                                                     .build();

            postRegion.tell(addPost, self());
            context().become(edit(postId));
        }).build();
    }

    private PartialFunction<Object, BoxedUnit> edit(String postId) {
        return ReceiveBuilder.match(Tick.class, tick -> {
            PostCommand changeBody = ImmutableChangeBody.builder()
                                                        .postId(postId)
                                                        .body("Something very interesting ...")
                                                        .build();
            postRegion.tell(changeBody, self());
            context().become(publish(postId));
        }).build();
    }

    private PartialFunction<Object, BoxedUnit> publish(String postId) {
        return ReceiveBuilder.match(Tick.class, tick -> {
            postRegion.tell(ImmutablePublishPost.of(postId), self());
            context().become(list());
        }).build();
    }

    private PartialFunction<Object, BoxedUnit> list() {
        return ReceiveBuilder.match(Tick.class,
                                    tick -> listingsRegion.tell(ImmutableGetPosts.of(currentAuthor()), self())
        ).match(ListPosts.class, posts -> {
            logger.info("Posts by {}: {}",
                        currentAuthor(),
                        posts.list().stream().map(PostSummary::title).collect(joining("\n\t", "\n\t", ""))
            );
            context().become(create());
        }).build();
    }

    private static class Tick {

        static final Tick INSTANCE = new Tick();
    }
}
