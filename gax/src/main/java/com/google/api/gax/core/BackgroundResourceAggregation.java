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
package com.google.api.gax.core;

import com.google.api.core.BetaApi;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Treats a collection of background resources as a single background resource. */
@BetaApi
public class BackgroundResourceAggregation implements BackgroundResource {

  private final List<BackgroundResource> resources;

  public BackgroundResourceAggregation(List<BackgroundResource> resources) {
    this.resources = resources;
  }

  @Override
  public void shutdown() {
    for (BackgroundResource resource : resources) {
      resource.shutdown();
    }
  }

  @Override
  public boolean isShutdown() {
    for (BackgroundResource resource : resources) {
      if (!resource.isShutdown()) {
        return false;
      }
    }
    return true;
  }

  @Override
  public boolean isTerminated() {
    for (BackgroundResource resource : resources) {
      if (!resource.isTerminated()) {
        return false;
      }
    }
    return true;
  }

  @Override
  public void shutdownNow() {
    for (BackgroundResource resource : resources) {
      resource.shutdownNow();
    }
  }

  @Override
  public boolean awaitTermination(long duration, TimeUnit unit) throws InterruptedException {
    for (BackgroundResource resource : resources) {
      // TODO subtract time already used up from previous resources
      boolean awaitResult = resource.awaitTermination(duration, unit);
      if (!awaitResult) {
        return false;
      }
    }
    return true;
  }

  @Override
  public final void close() throws Exception {
    shutdown();
  }
}
