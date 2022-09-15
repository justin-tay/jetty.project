//
// ========================================================================
// Copyright (c) 1995-2022 Mort Bay Consulting Pty Ltd and others.
//
// This program and the accompanying materials are made available under the
// terms of the Eclipse Public License v. 2.0 which is available at
// https://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
// which is available at https://www.apache.org/licenses/LICENSE-2.0.
//
// SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
// ========================================================================
//

package org.eclipse.jetty.io.internal;

import java.util.function.BiPredicate;

import org.eclipse.jetty.io.Content;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.IteratingNestedCallback;

public class ContentCopier extends IteratingNestedCallback
{
    private final Content.Source source;
    private final Content.Sink sink;
    private final BiPredicate<Content.Chunk, Callback> chunkHandler;
    private Content.Chunk current;
    private boolean terminated;

    public ContentCopier(Content.Source source, Content.Sink sink, BiPredicate<Content.Chunk, Callback> chunkHandler, Callback callback)
    {
        super(callback);
        this.source = source;
        this.sink = sink;
        this.chunkHandler = chunkHandler;
    }

    @Override
    public InvocationType getInvocationType()
    {
        return InvocationType.NON_BLOCKING;
    }

    @Override
    protected Action process() throws Throwable
    {
        if (terminated)
            return Action.SUCCEEDED;

        current = source.read();

        if (current == null)
        {
            source.demand(this::iterate);
            return Action.IDLE;
        }

        if (chunkHandler != null && chunkHandler.test(current, this))
            return Action.SCHEDULED;

        if (current instanceof Error error)
            throw error.getCause();

        sink.write(current.isLast(), current.getByteBuffer(), this);
        return Action.SCHEDULED;
    }

    @Override
    public void succeeded()
    {
        terminated = current.isLast();
        current.release();
        current = null;
        super.succeeded();
    }

    @Override
    public void failed(Throwable x)
    {
        if (current != null)
            current.release();
        current = null;
        source.fail(x);
        super.failed(x);
    }
}