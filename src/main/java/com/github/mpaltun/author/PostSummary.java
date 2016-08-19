package com.github.mpaltun.author;

import java.io.Serializable;

import org.immutables.value.Value.Immutable;
import org.immutables.value.Value.Parameter;

@Immutable(builder = false)
public interface PostSummary extends Serializable {

    @Parameter
    String author();

    @Parameter
    String postId();

    @Parameter
    String title();

}
