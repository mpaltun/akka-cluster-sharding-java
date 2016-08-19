package com.github.mpaltun.author;

import static java.util.concurrent.TimeUnit.MINUTES;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.mpaltun.bot.ImmutableListPosts;

import akka.actor.PoisonPill;
import akka.actor.Props;
import akka.actor.ReceiveTimeout;
import akka.cluster.sharding.ShardRegion;
import akka.japi.pf.ReceiveBuilder;
import akka.persistence.AbstractPersistentActor;
import scala.PartialFunction;
import scala.concurrent.duration.Duration;
import scala.runtime.BoxedUnit;

public class AuthorListing extends AbstractPersistentActor {

    public static final String SHARD = "AuthorListing";

    private static final Logger logger = LoggerFactory.getLogger(AuthorListing.class);

    private List<PostSummary> posts = new ArrayList<>();

    private AuthorListing() {
        context().setReceiveTimeout(Duration.create(2, MINUTES));
    }

    @Override
    public PartialFunction<Object, BoxedUnit> receiveRecover() {
        return ReceiveBuilder.match(PostSummary.class, posts::add).build();

    }

    @Override
    public PartialFunction<Object, BoxedUnit> receiveCommand() {
        return ReceiveBuilder.match(PostSummary.class, this::handlePostSummary)
                             .match(GetPosts.class, this::handleGetPosts)
                             .match(ReceiveTimeout.class, this::handleReceiveTimeout)
                             .build();
    }

    @Override
    public String persistenceId() {
        return self().path().parent().name() + "-" + self().path().name();
    }

    public static ShardRegion.MessageExtractor shardExtractor() {
        return new AuthorListingShardMessageExtractor();
    }

    public static Props props() {
        return Props.create(AuthorListing.class, AuthorListing::new);
    }

    private void handlePostSummary(PostSummary s) {
        persist(s, event -> {
            posts.add(event);
            logger.info("Post added to {}'s list: {}", s.author(), s.title());
        });
    }

    private void handleGetPosts(GetPosts request) {
        sender().tell(ImmutableListPosts.of(posts), self());
    }

    private void handleReceiveTimeout(ReceiveTimeout timeout) {
        context().parent().tell(new ShardRegion.Passivate(PoisonPill.getInstance()), self());
    }

    private static class AuthorListingShardMessageExtractor extends ShardRegion.HashCodeMessageExtractor {

        AuthorListingShardMessageExtractor() {
            super(100);
        }

        @Override
        public String entityId(Object o) {
            if (o instanceof PostSummary) {
                return ((PostSummary) o).author();
            }

            if (o instanceof GetPosts) {
                return ((GetPosts) o).author();
            }
            return null;
        }
    }
}
