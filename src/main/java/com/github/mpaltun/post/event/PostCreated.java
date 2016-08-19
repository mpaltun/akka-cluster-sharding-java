package com.github.mpaltun.post.event;

import org.immutables.value.Value.Immutable;
import org.immutables.value.Value.Parameter;

import com.github.mpaltun.post.command.PostContent;

@Immutable(builder = false)
public interface PostCreated extends Event {

    @Parameter
    PostContent content();

}
