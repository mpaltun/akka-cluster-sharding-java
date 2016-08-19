package com.github.mpaltun.author;

import java.io.Serializable;

import org.immutables.value.Value.Immutable;
import org.immutables.value.Value.Parameter;

@Immutable(builder = false)
public interface GetPosts extends Serializable {

    @Parameter
    String author();

}
