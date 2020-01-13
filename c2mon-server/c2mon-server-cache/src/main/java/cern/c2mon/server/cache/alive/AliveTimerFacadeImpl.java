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
package cern.c2mon.server.cache.alive;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import cern.c2mon.server.cache.AliveTimerCache;
import cern.c2mon.server.cache.AliveTimerFacade;
import cern.c2mon.server.cache.exception.CacheElementNotFoundException;
import cern.c2mon.server.common.alive.AliveTimer;
import cern.c2mon.server.common.alive.AliveTimerCacheObject;
import cern.c2mon.server.common.equipment.AbstractEquipment;
import cern.c2mon.server.common.equipment.Equipment;
import cern.c2mon.server.common.process.Process;

/**
 * Implementation of the AliverTimerFacade.
 *
 * @author Mark Brightwell
 *
 */
@Slf4j
@Service
public class AliveTimerFacadeImpl implements AliveTimerFacade {

  private static final String CANNOT_LOCATE_THE_ALIVE_TIMER_IN_THE_CACHE_ID_IS = "Cannot locate the AliveTimer in the cache (Id is ";
private AliveTimerCache aliveTimerCache;

  @Autowired
  public AliveTimerFacadeImpl(AliveTimerCache aliveTimerCache) {
    super();
    this.aliveTimerCache = aliveTimerCache;
  }

  @Override
  public boolean isRegisteredAliveTimer(final Long id) {
    return aliveTimerCache.hasKey(id); //TODO check DB also but do not load
  }

  @Override
  public void update(final Long aliveId) {
    aliveTimerCache.acquireWriteLockOnKey(aliveId);
    try {
      AliveTimer aliveTimer = aliveTimerCache.get(aliveId);
      update(aliveTimer);
      aliveTimerCache.put(aliveId, aliveTimer);
    } catch (CacheElementNotFoundException cacheEx) {
      log.error(CANNOT_LOCATE_THE_ALIVE_TIMER_IN_THE_CACHE_ID_IS + aliveId + ") - unable to update it.", cacheEx);
    } catch (Exception e) {
      log.error("updatedAliveTimer() failed for an unknown reason: ", e);
    } finally {
      aliveTimerCache.releaseWriteLockOnKey(aliveId);
    }
  }
  /**
   * Update this alive timer. This method will reset the time of the last
   * update and thus relaunch the alive timer.
   */
  private void update(final AliveTimer aliveTimer) {
    // We only update the alive timer if the timestamp is >= the timestamp
    // of the last update. Otherwise, we ignore the update request and return
    // false
    // This is to avoid that alive timers that have been delayed on the network
    // start confusing the alive timer mechanism.


    aliveTimer.setActive(true);
    aliveTimer.setLastUpdate(System.currentTimeMillis());
    if (log.isDebugEnabled()) {
      StringBuilder str = new StringBuilder("Updated alive timer for ");
      str.append(AliveTimer.ALIVE_TYPE_PROCESS + " ");
      str.append(aliveTimer.getRelatedName());
      str.append(".");
      log.debug(str.toString());
    }
  }

  @Override
  public void start(Long id) {
    aliveTimerCache.acquireWriteLockOnKey(id);
    try {
      AliveTimer aliveTimer = aliveTimerCache.get(id);
      start(aliveTimer);
      aliveTimerCache.put(id, aliveTimer);
    } catch (CacheElementNotFoundException cacheEx) {
      log.error(CANNOT_LOCATE_THE_ALIVE_TIMER_IN_THE_CACHE_ID_IS + id + ") - unable to start it.");
    } catch (Exception e) {
      log.error("Unable to start the alive timer " + id, e);
    } finally {
      aliveTimerCache.releaseWriteLockOnKey(id);
    }
  }

  /**
   * Activate this alive timer.
   */
  private void start(final AliveTimer aliveTimer) {
    if (!aliveTimer.isActive()) {
      if (log.isDebugEnabled()) {
        StringBuilder str = new StringBuilder("start() : starting alive for ");
        str.append(AliveTimer.ALIVE_TYPE_PROCESS + " ");
        str.append(aliveTimer.getRelatedName());
        str.append(".");
        log.debug(str.toString());
      }
      aliveTimer.setActive(true);
      aliveTimer.setLastUpdate(System.currentTimeMillis());
    }
  }

  @Override
  public void stop(Long id) {
    aliveTimerCache.acquireWriteLockOnKey(id);
    log.debug("Stopping alive timer " + id + " and dependent alive timers.");
    try {
      AliveTimer aliveTimer = aliveTimerCache.get(id);
      stop(aliveTimer);
      aliveTimerCache.put(id, aliveTimer);
    } catch (CacheElementNotFoundException cacheEx) {
      log.error(CANNOT_LOCATE_THE_ALIVE_TIMER_IN_THE_CACHE_ID_IS + id + ") - unable to stop it.");
    } catch (Exception e) {
      log.error("Unable to stop the alive timer " + id, e);
    } finally {
      aliveTimerCache.releaseWriteLockOnKey(id);
    }
  }

  /**
   * Deactivate this alive timer if activated.
   */
  private void stop(final AliveTimer aliveTimer) {
    if (aliveTimer.isActive()) {
      if (log.isDebugEnabled()) {
        StringBuilder str = new StringBuilder("stop() : stopping alive for ");
        str.append(aliveTimer.getAliveTypeDescription() + " ");
        str.append(aliveTimer.getRelatedName());
        str.append(".");
        log.debug(str.toString());
      }
      aliveTimer.setActive(false);
      aliveTimer.setLastUpdate(System.currentTimeMillis());
    }
  }

  /**
   * Check whether this alive timer has expired.
   * @return true if the alive timer is active and it has not been updated since
   * at least "aliveInterval" milliseconds.
   */
  @Override
  public boolean hasExpired(final Long aliveTimerId) {
    aliveTimerCache.acquireReadLockOnKey(aliveTimerId);
    try {
        AliveTimer aliveTimer = aliveTimerCache.get(aliveTimerId);
        return (System.currentTimeMillis() - aliveTimer.getLastUpdate() > aliveTimer.getAliveInterval() + aliveTimer.getAliveInterval() / 3);
    } finally {
      aliveTimerCache.releaseReadLockOnKey(aliveTimerId);
    }
  }

  @Override
  public void startAllTimers() {
    log.debug("Starting all alive timers in cache.");
    try {
      for (Long currentId : aliveTimerCache.getKeys()) {
        start(currentId);
      }
    } catch (Exception e) {
      log.error("Unable to retrieve list of alive timers from cache when attempting to start the timers.", e);
    }
  }

  @Override
  public void stopAllTimers() {
    log.debug("Stopping all alive timers in the cache.");
    try {
      for (Long currentId : aliveTimerCache.getKeys()) {
        stop(currentId);
      }
    } catch (Exception e) {
      log.error("Unable to retrieve list of alive timers from cache when attempting to stop all timers.", e);
    }
  }

  @Override
  public void generateFromEquipment(AbstractEquipment abstractEquipment) {
    String type;
    if (abstractEquipment instanceof Equipment) {
      type = AliveTimer.ALIVE_TYPE_EQUIPMENT;
    } else {
      type = AliveTimer.ALIVE_TYPE_SUBEQUIPMENT;
    }
    AliveTimer aliveTimer = new AliveTimerCacheObject(abstractEquipment.getAliveTagId(), abstractEquipment.getId(), abstractEquipment.getName(),
                                                      abstractEquipment.getStateTagId(), type, abstractEquipment.getAliveInterval());
    aliveTimerCache.put(aliveTimer.getId(), aliveTimer);
  }

  @Override
  public void generateFromProcess(Process process) {
    AliveTimer aliveTimer = new AliveTimerCacheObject(process.getAliveTagId(), process.getId(), process.getName(),
        process.getStateTagId(), AliveTimer.ALIVE_TYPE_PROCESS, process.getAliveInterval());
    aliveTimerCache.put(aliveTimer.getId(), aliveTimer);
  }

}
