/*
 * Copyright 2017, Google Inc. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 *     * Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 * copyright notice, this list of conditions and the following disclaimer
 * in the documentation and/or other materials provided with the
 * distribution.
 *     * Neither the name of Google Inc. nor the names of its
 * contributors may be used to endorse or promote products derived from
 * this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.google.api.gax.rpc;

import com.google.api.core.ApiClock;
import com.google.api.gax.core.BackgroundResource;
import com.google.api.gax.core.BaseBackgroundResource;
import com.google.api.gax.core.ExecutorProvider;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.api.gax.rpc.testing.FakeClientSettings;
import com.google.auth.Credentials;
import com.google.common.collect.ImmutableList;
import com.google.common.truth.Truth;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public class ClientContextTest {

  private static class InterceptingExecutor extends ScheduledThreadPoolExecutor {
    boolean shutdownCalled = false;

    public InterceptingExecutor(int corePoolSize) {
      super(corePoolSize);
    }

    public void shutdown() {
      shutdownCalled = true;
    }
  }

  private static class FakeExecutorProvider implements ExecutorProvider {
    ScheduledExecutorService executor;
    boolean shouldAutoClose;

    FakeExecutorProvider(ScheduledExecutorService executor, boolean shouldAutoClose) {
      this.executor = executor;
      this.shouldAutoClose = shouldAutoClose;
    }

    @Override
    public boolean shouldAutoClose() {
      return shouldAutoClose;
    }

    @Override
    public ScheduledExecutorService getExecutor() {
      return executor;
    }
  }

  private static class FakeTransport extends Transport {
    boolean shouldAutoClose;
    boolean shutdownCalled = false;

    FakeTransport(boolean shouldAutoClose) {
      this.shouldAutoClose = shouldAutoClose;
    }

    @Override
    public String getTransportName() {
      return "FakeTransport";
    }

    @Override
    public List<BackgroundResource> getBackgroundResources() {
      ImmutableList.Builder<BackgroundResource> backgroundResources = ImmutableList.builder();
      if (shouldAutoClose) {
        backgroundResources.add(
            new BaseBackgroundResource() {
              @Override
              public void shutdown() {
                shutdownCalled = true;
              }
            });
      }
      return backgroundResources.build();
    }
  }

  private static class FakeTransportProvider implements TransportProvider {
    boolean needsExecutor;
    Transport transport;

    FakeTransportProvider(Transport transport, boolean needsExecutor) {
      this.transport = transport;
      this.needsExecutor = needsExecutor;
    }

    @Override
    public boolean needsExecutor() {
      return needsExecutor;
    }

    @Override
    public Transport getTransport() throws IOException {
      if (needsExecutor) {
        throw new IllegalStateException("Needs Executor");
      }
      return transport;
    }

    @Override
    public Transport getTransport(ScheduledExecutorService executor) throws IOException {
      if (!needsExecutor) {
        throw new IllegalStateException("Does not need Executor");
      }
      return transport;
    }

    @Override
    public String getTransportName() {
      return "FakeTransport";
    }
  }

  @Test
  public void testNoAutoCloseContextNeedsNoExecutor() throws Exception {
    runTest(false, false);
  }

  @Test
  public void testWithAutoCloseContextNeedsNoExecutor() throws Exception {
    runTest(true, false);
  }

  @Test
  public void testWithAutoCloseContextNeedsExecutor() throws Exception {
    runTest(true, true);
  }

  private void runTest(boolean shouldAutoClose, boolean contextNeedsExecutor) throws Exception {
    FakeClientSettings.Builder builder = new FakeClientSettings.Builder();

    InterceptingExecutor executor = new InterceptingExecutor(1);
    ExecutorProvider executorProvider = new FakeExecutorProvider(executor, shouldAutoClose);
    FakeTransport transportContext = new FakeTransport(shouldAutoClose);
    FakeTransportProvider transportProvider =
        new FakeTransportProvider(transportContext, contextNeedsExecutor);

    Credentials credentials = Mockito.mock(Credentials.class);
    ApiClock clock = Mockito.mock(ApiClock.class);

    builder.setExecutorProvider(executorProvider);
    builder.setTransportProvider(transportProvider);
    builder.setCredentialsProvider(FixedCredentialsProvider.create(credentials));
    builder.setClock(clock);

    FakeClientSettings settings = builder.build();
    ClientContext clientContext = ClientContext.create(settings);

    Truth.assertThat(clientContext.getExecutor()).isSameAs(executor);
    Truth.assertThat(clientContext.getTransportContext()).isSameAs(transportContext);
    Truth.assertThat(clientContext.getCredentials()).isSameAs(credentials);
    Truth.assertThat(clientContext.getClock()).isSameAs(clock);

    Truth.assertThat(executor.shutdownCalled).isFalse();
    Truth.assertThat(transportContext.shutdownCalled).isFalse();

    for (BackgroundResource backgroundResource : clientContext.getBackgroundResources()) {
      backgroundResource.shutdown();
    }

    Truth.assertThat(executor.shutdownCalled).isEqualTo(shouldAutoClose);
    Truth.assertThat(transportContext.shutdownCalled).isEqualTo(shouldAutoClose);
  }
}
