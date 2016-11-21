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
package cern.c2mon.server.shorttermlog.logger;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;

import cern.c2mon.pmanager.IFallback;
import cern.c2mon.pmanager.persistence.IPersistenceManager;
import cern.c2mon.server.shorttermlog.structure.ExpressionLog;
import cern.c2mon.server.shorttermlog.structure.Loggable;
import cern.c2mon.server.shorttermlog.structure.LoggerConverter;
import cern.c2mon.shared.client.expression.Expression;
import cern.c2mon.shared.common.Cacheable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * @author Franz Ritter
 */
@Service
public class ExpressionLogger  {

  /**
   * The fallback persistence manager.
   */
  private IPersistenceManager persistenceManager;

  /**
   * Unique constructor.
   *
   * @param persistenceManager the persistence manager
   */
  @Autowired
  public ExpressionLogger(@Qualifier("stlExpressionPersistenceManager") final IPersistenceManager persistenceManager) {
    super();
    this.persistenceManager = persistenceManager;
  }

  public void log(final Collection<Expression> cacheExpressions, Long tagId, Timestamp serverTimestamp) {
    ArrayList<IFallback> shortTermLogObjects = new ArrayList<>();

    // Convert the list of DataTagCacheObjects to DataTagShortTermLog objects (IFallback objects)
    for (Expression expression : cacheExpressions) {
        if (expression!= null && expression.getName() != null) {
          shortTermLogObjects.add(new ExpressionLog(expression, tagId, serverTimestamp));
        }
    }
    persistenceManager.storeData(shortTermLogObjects);
  }

}