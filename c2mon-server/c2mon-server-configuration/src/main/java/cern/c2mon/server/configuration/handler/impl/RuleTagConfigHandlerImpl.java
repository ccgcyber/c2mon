/******************************************************************************
 * Copyright (C) 2010-2018 CERN. All rights not expressly granted are reserved.
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
package cern.c2mon.server.configuration.handler.impl;

import java.util.Properties;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.UnexpectedRollbackException;

import cern.c2mon.server.cache.RuleTagCache;
import cern.c2mon.server.configuration.handler.RuleTagConfigHandler;
import cern.c2mon.server.configuration.handler.transacted.RuleTagConfigTransacted;
import cern.c2mon.server.configuration.impl.ConfigurationUpdateImpl;
import cern.c2mon.server.rule.RuleEvaluator;
import cern.c2mon.shared.client.configuration.ConfigurationElement;
import cern.c2mon.shared.client.configuration.ConfigurationElementReport;

/**
 * See interface documentation.
 *
 * @author Mark Brightwell
 *
 */
@Service
@Slf4j
public class RuleTagConfigHandlerImpl implements RuleTagConfigHandler {

  /**
   * Helper class for accessing the List of registered listeners
   * for configuration updates.
   */
  private ConfigurationUpdateImpl configurationUpdateImpl;

  /**
   * Transacted bean.
   */
  @Autowired
  private RuleTagConfigTransacted ruleTagConfigTransacted;

  private RuleTagCache ruleTagCache;

  private RuleEvaluator ruleEvaluator;

  /**
   * Default constructor
   *
   * @param ruleTagCache
   * @param ruleEvaluator
   * @param configurationUpdateImpl
   */
  @Autowired
  public RuleTagConfigHandlerImpl(final RuleTagCache ruleTagCache, final RuleEvaluator ruleEvaluator, final ConfigurationUpdateImpl configurationUpdateImpl) {
    this.ruleTagCache = ruleTagCache;
    this.ruleEvaluator = ruleEvaluator;
    this.configurationUpdateImpl = configurationUpdateImpl;
  }

  @Override
  public void removeRuleTag(final Long id, final ConfigurationElementReport elementReport) {
    ruleTagConfigTransacted.doRemoveRuleTag(id, elementReport);
    ruleTagCache.remove(id); //will be skipped if rollback exception thrown in do method
  }

  @Override
  public void createRuleTag(ConfigurationElement element) throws IllegalAccessException {
    ruleTagConfigTransacted.doCreateRuleTag(element);
    ruleEvaluator.evaluateRule(element.getEntityId());
    if (log.isTraceEnabled()) {
      log.trace("createRuleTag - Notifying Configuration update listeners");
    }
    this.configurationUpdateImpl.notifyListeners(element.getEntityId());
  }

  @Override
  public void updateRuleTag(Long id, Properties elementProperties) throws IllegalAccessException {
	  try {
		  ruleTagConfigTransacted.doUpdateRuleTag(id, elementProperties);
		  ruleEvaluator.evaluateRule(id);
		  if (log.isTraceEnabled()) {
			  log.trace("updateRuleTag - Notifying Configuration update listeners");
		  }
		  this.configurationUpdateImpl.notifyListeners(id);
	  } catch (UnexpectedRollbackException e) {
		  log.error("Rolling back Rule update in cache");
		  ruleTagCache.remove(id);
		  ruleTagCache.loadFromDb(id);
		  throw e;
	  }
  }

  @Override
  public void addAlarmToTag(Long tagId, Long alarmId) {
    ruleTagConfigTransacted.addAlarmToTag(tagId, alarmId);
  }

  @Override
  public void addRuleToTag(Long tagId, Long ruleId) {
    ruleTagConfigTransacted.addRuleToTag(tagId, ruleId);
  }

  @Override
  public void removeAlarmFromTag(Long tagId, Long alarmId) {
    ruleTagConfigTransacted.removeAlarmFromTag(tagId, alarmId);
  }

  @Override
  public void removeRuleFromTag(Long tagId, Long ruleId) {
    ruleTagConfigTransacted.removeRuleFromTag(tagId, ruleId);
  }

}
