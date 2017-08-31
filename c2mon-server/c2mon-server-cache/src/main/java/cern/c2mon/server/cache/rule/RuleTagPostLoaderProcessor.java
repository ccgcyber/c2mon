/******************************************************************************
 * Copyright (C) 2010-2016 CERN. All rights not expressly granted are reserved.
 *
 * This file is part of the CERN Control and Monitoring Platform 'C2MON'.
 * C2MON is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation, either version 3 of the license.
 *
 * C2MON is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with C2MON. If not, see <http://www.gnu.org/licenses/>.
 *****************************************************************************/
package cern.c2mon.server.cache.rule;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import cern.c2mon.server.cache.ClusterCache;
import cern.c2mon.server.cache.RuleTagCache;
import cern.c2mon.server.cache.RuleTagFacade;
import cern.c2mon.server.common.rule.RuleTag;

/**
 * Manages the multi threaded loading of the rule
 * parent ids at start up.
 *
 * @author Mark Brightwell
 */
@Service
@Slf4j
public class RuleTagPostLoaderProcessor {

  private RuleTagFacade ruleTagFacade;

  private RuleTagCache ruleTagCache;

  private ClusterCache clusterCache;

  private ThreadPoolTaskExecutor executor;

  /**
   * Thread pool settings.
   */
  private int threadPoolMax = 16;
  private int threadPoolMin = 4;

  private static final int THREAD_IDLE_LIMIT = 5; // in seconds
  private static final String THREAD_NAME_PREFIX = "RuleLoader-";

  /**
   * Cluster Cache key to avoid loading twice the parent rule ids at startup
   */
  public static final String ruleCachePostProcessedKey = "c2mon.cache.rule.ruleCachePostProcessed";

  @Autowired
  public RuleTagPostLoaderProcessor(RuleTagFacade ruleTagFacade, RuleTagCache ruleTagCache, ClusterCache clusterCache) {
    super();
    this.ruleTagFacade = ruleTagFacade;
    this.ruleTagCache = ruleTagCache;
    this.clusterCache = clusterCache;
  }

  /**
   * Loads parent ids in batches of 500 on bean creation,
   * if the distributed cache is being initialised.
   */
  @PostConstruct
  public void loadRuleParentIds() {
    log.trace("Entering loadRuleParentIds()...");

    log.trace("Trying to get cache lock for " + RuleTagCache.cacheInitializedKey);
    clusterCache.acquireWriteLockOnKey(RuleTagCache.cacheInitializedKey);
    try {
      Boolean isRuleCachePostProcessed = Boolean.FALSE;
      if (clusterCache.hasKey(ruleCachePostProcessedKey)) {
        isRuleCachePostProcessed = (Boolean) clusterCache.getCopy(ruleCachePostProcessedKey);
      }
      if (!isRuleCachePostProcessed.booleanValue()) {
        log.debug("Setting parent ids for rules...");

        initializeThreadPool();

        LoaderTask task = new LoaderTask();
        int counter = 0;
        for (Long key : ruleTagCache.getKeys()) {
          task.addKey(key);
          counter++;
          if (counter == 500) {
            executor.execute(task);
            task = new LoaderTask();
            counter = 0;
          }
        }
        executor.execute(task);
        executor.shutdown();
        try {
          executor.getThreadPoolExecutor().awaitTermination(1200, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
          log.warn("Exception caught while waiting for rule parent id loading threads to complete (waited longer then timeout?): ", e);
        }
        log.debug("Rule parent ids set.");
        clusterCache.put(ruleCachePostProcessedKey, Boolean.TRUE);
      } else {
        log.info("Cache " + RuleTagCache.cacheInitializedKey + " was already initialized");
      }
    } finally {
      clusterCache.releaseWriteLockOnKey(RuleTagCache.cacheInitializedKey);
      log.trace("Released cache lock .. for {}", RuleTagCache.cacheInitializedKey);
    }

    log.trace("Leaving loadRuleParentIds()");
  }

  private void initializeThreadPool() {
    executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(threadPoolMin);
    executor.setMaxPoolSize(threadPoolMax);
    executor.setKeepAliveSeconds(THREAD_IDLE_LIMIT);
    executor.setThreadNamePrefix(THREAD_NAME_PREFIX);
    executor.initialize();
  }

  private class LoaderTask implements Runnable {

    private List<Long> keyList = new LinkedList<Long>();

    public void addKey(Long key) {
      keyList.add(key);
    }

    @Override
    public void run() {
      for (Long ruleKey : keyList) {
        RuleTag ruleTag = ruleTagCache.get(ruleKey);
        //if not empty, already processed
        if (ruleTag.getProcessIds().isEmpty()) {
          ruleTagFacade.setParentSupervisionIds(ruleTag);
          ruleTagCache.putQuiet(ruleTag);
        }
      }
    }
  }

  /**
   * Setter method.
   *
   * @param threadPoolMax the threadPoolMax to set
   */
  public void setThreadPoolMax(int threadPoolMax) {
    this.threadPoolMax = threadPoolMax;
  }

  /**
   * Setter method.
   *
   * @param threadPoolMin the threadPoolMin to set
   */
  public void setThreadPoolMin(int threadPoolMin) {
    this.threadPoolMin = threadPoolMin;
  }

}