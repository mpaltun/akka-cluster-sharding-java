package com.github.mpaltun.post.command;

import java.io.Serializable;

import org.immutables.value.Value;
import org.immutables.value.Value.Immutable;

@Immutable(builder = false)
public interface PostContent extends Serializable {

    PostContent EMPTY = ImmutablePostContent.of("", "", "");

    @Value.Parameter(order = 1)
    String author();

    @Value.Parameter(order = 2)
    String title();

    @Value.Parameter(order = 3)
    String body();

}
