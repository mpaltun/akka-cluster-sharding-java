package com.github.mpaltun.bot;

import java.io.Serializable;
import java.util.List;

import org.immutables.value.Value.Immutable;
import org.immutables.value.Value.Parameter;

import com.github.mpaltun.author.PostSummary;

@Immutable(builder = false)
public interface ListPosts extends Serializable {

    @Parameter
    List<PostSummary> list();

}
