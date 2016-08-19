package com.github.mpaltun.post;

import static com.google.common.base.Strings.isNullOrEmpty;
import static java.util.concurrent.TimeUnit.MINUTES;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.mpaltun.author.ImmutablePostSummary;
import com.github.mpaltun.post.command.ChangeBody;
import com.github.mpaltun.post.command.CreatePost;
import com.github.mpaltun.post.command.GetContent;
import com.github.mpaltun.post.command.PostCommand;
import com.github.mpaltun.post.command.PostContent;
import com.github.mpaltun.post.command.PublishPost;
import com.github.mpaltun.post.event.BodyChanged;
import com.github.mpaltun.post.event.Event;
import com.github.mpaltun.post.event.ImmutableBodyChanged;
import com.github.mpaltun.post.event.ImmutablePostCreated;
import com.github.mpaltun.post.event.ImmutablePostPublished;
import com.github.mpaltun.post.event.PostCreated;
import com.github.mpaltun.post.event.PostPublished;

import akka.actor.ActorRef;
import akka.actor.PoisonPill;
import akka.actor.Props;
import akka.actor.ReceiveTimeout;
import akka.cluster.sharding.ShardRegion;
import akka.japi.pf.ReceiveBuilder;
import akka.persistence.AbstractPersistentActor;
import scala.PartialFunction;
import scala.concurrent.duration.Duration;
import scala.runtime.BoxedUnit;

public final class Post extends AbstractPersistentActor {

    public static final String SHARD = "Post";

    private static final Logger logger = LoggerFactory.getLogger(Post.class);

    private final ActorRef authorListing;
    private PostState state;

    private Post(ActorRef authorListing) {
        this.authorListing = authorListing;
        this.state = PostState.INITIAL;

        // passivate the entity when no activity
        context().setReceiveTimeout(Duration.create(2, MINUTES));
    }

    @Override
    public void unhandled(Object message) {
        if (message instanceof ReceiveTimeout) {
            context().parent().tell(new ShardRegion.Passivate(PoisonPill.getInstance()), self());
        }
        else {
            super.unhandled(message);
        }
    }

    @Override
    public PartialFunction<Object, BoxedUnit> receiveRecover() {
        return ReceiveBuilder.match(PostCreated.class, this::addPost)
                             .match(PostPublished.class, this::publishPost)
                             .match(Event.class, this::handleEvent)
                             .build();
    }

    @Override
    public PartialFunction<Object, BoxedUnit> receiveCommand() {
        return ReceiveBuilder.match(GetContent.class, this::handleGetContent)
                             .match(CreatePost.class, this::handleCreatePost)
                             .build();

    }

    @Override
    public String persistenceId() {
        return self().path().parent().name() + "-" + self().path().name();
    }

    public static ShardRegion.MessageExtractor shardExtractor() {
        return new PostShardMessageExtractor();
    }

    public static Props props(ActorRef authorListing) {
        return Props.create(Post.class, () -> new Post(authorListing));
    }

    private PartialFunction<Object, BoxedUnit> created() {
        return ReceiveBuilder.match(GetContent.class, this::handleGetContent)
                             .match(ChangeBody.class, this::handleChangeBody)
                             .match(PublishPost.class, this::handlePublish)
                             .build();
    }

    private PartialFunction<Object, BoxedUnit> published() {
        return ReceiveBuilder.match(GetContent.class, this::handleGetContent).build();
    }

    private void handlePublish(PublishPost command) {
        persist(ImmutablePostPublished.of(), e -> {
            publishPost(e);

            PostContent c = state.content();
            logger.info("Post published: {}", c.title());
            authorListing.tell(ImmutablePostSummary.of(c.author(), command.postId(), c.title()), self());
        });
    }

    private void handleChangeBody(ChangeBody command) {
        BodyChanged bodyChanged = ImmutableBodyChanged.of(command.body());
        persist(bodyChanged, this::changeBody);
    }

    private void handleCreatePost(CreatePost command) {
        PostContent content = command.content();
        if (!isNullOrEmpty(content.author()) && !isNullOrEmpty(content.title())) {
            persist(ImmutablePostCreated.of(content), this::addPost);
        }
    }

    private void handleGetContent(GetContent ignored) {
        sender().tell(state.content(), self());
    }

    private void addPost(PostCreated event) {
        state = state.updated(event);
        context().become(created());
        logger.info("New post saved: {}", state.content().title());
    }

    private void changeBody(BodyChanged event) {
        state = state.updated(event);
        logger.info("Post changed: {}", state.content().title());
    }

    private void publishPost(PostPublished event) {
        state = state.updated(event);
        context().become(published());
    }

    private void handleEvent(Event event) {
        state = state.updated(event);
    }

    private static class PostShardMessageExtractor extends ShardRegion.HashCodeMessageExtractor {

        PostShardMessageExtractor() {
            super(100);
        }

        @Override
        public String entityId(Object o) {
            if (o instanceof PostCommand) {
                return ((PostCommand) o).postId();
            }

            return null;
        }
    }
}
