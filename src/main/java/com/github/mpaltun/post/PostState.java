package com.github.mpaltun.post;

import org.immutables.value.Value.Immutable;
import org.immutables.value.Value.Parameter;
import org.immutables.value.Value.Style;

import com.github.mpaltun.post.command.ImmutablePostContent;
import com.github.mpaltun.post.command.PostContent;
import com.github.mpaltun.post.event.BodyChanged;
import com.github.mpaltun.post.event.Event;
import com.github.mpaltun.post.event.PostCreated;
import com.github.mpaltun.post.event.PostPublished;

@Style(get = {"is*"})
@Immutable(builder = false)
abstract class PostState {

    static final PostState INITIAL = ImmutablePostState.of(PostContent.EMPTY, false);

    @Parameter
    abstract PostContent content();

    @Parameter
    abstract boolean isPublished();

    PostState updated(PostCreated e) {
        return ImmutablePostState.of(e.content(), isPublished());
    }

    PostState updated(BodyChanged e) {
        PostContent content = ImmutablePostContent.copyOf(content()).withBody(e.body());
        return ImmutablePostState.copyOf(this).withContent(content);
    }

    PostState updated(PostPublished ignored) {
        return ImmutablePostState.of(content(), true);
    }

    PostState updated(Event event) {
        throw new UnsupportedOperationException("unknown event " + event);
    }
}
