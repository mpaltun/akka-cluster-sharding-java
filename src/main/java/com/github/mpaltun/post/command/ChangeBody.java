package com.github.mpaltun.post.command;

import org.immutables.value.Value.Immutable;

@Immutable
public interface ChangeBody extends PostCommand {

    String body();
}
