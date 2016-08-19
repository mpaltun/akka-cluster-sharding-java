package com.github.mpaltun.post.event;

import org.immutables.value.Value.Immutable;

@Immutable(singleton = true, builder = false)
public interface PostPublished extends Event {}
