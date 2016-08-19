package com.github.mpaltun.post.event;

import org.immutables.value.Value.Immutable;
import org.immutables.value.Value.Parameter;

@Immutable(builder = false)
public interface BodyChanged extends Event {

    @Parameter
    String body();

}
