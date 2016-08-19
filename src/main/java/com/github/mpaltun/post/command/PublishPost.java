package com.github.mpaltun.post.command;

import org.immutables.value.Value.Immutable;
import org.immutables.value.Value.Parameter;

@Immutable(builder = false)
public interface PublishPost extends PostCommand {

    @Parameter
    @Override
    String postId();
}
