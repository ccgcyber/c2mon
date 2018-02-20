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
package cern.c2mon.server.elasticsearch.alarm;

import cern.c2mon.pmanager.persistence.exception.IDBPersistenceException;
import cern.c2mon.server.common.alarm.AlarmCacheObject;
import cern.c2mon.server.elasticsearch.Indices;
import cern.c2mon.server.elasticsearch.config.BaseElasticsearchIntegrationTest;
import cern.c2mon.server.elasticsearch.util.EntityUtils;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexResponse;
import org.elasticsearch.action.search.SearchResponse;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.sql.Timestamp;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;

/**
 * @author Alban Marguet
 * @author Justin Lewis Salmon
 */
public class AlarmDocumentIndexerTests extends BaseElasticsearchIntegrationTest {

  @Autowired
  private AlarmDocumentIndexer indexer;

  @Test
  public void indexAlarm() throws IDBPersistenceException {
    AlarmCacheObject alarm = (AlarmCacheObject) EntityUtils.createAlarm();
    alarm.setTimestamp(new Timestamp(0));

    AlarmDocument document = new AlarmValueDocumentConverter().convert(alarm);
    indexer.storeData(document);

    // Refresh the index to make sure the document is searchable
    String index = Indices.indexFor(document);
    client.getClient().admin().indices().prepareRefresh(index).execute().actionGet();

    // Make sure the index was created
    assertTrue(Indices.exists(index));

    // Make sure the alarm exists in the index
    SearchResponse response = client.getClient().prepareSearch(index).setTypes("alarm").execute().actionGet();
    assertEquals(response.getHits().totalHits(), 1);

    // Clean up
    DeleteIndexResponse deleteResponse = client.getClient().admin().indices().prepareDelete(index).execute().actionGet();
    assertTrue(deleteResponse.isAcknowledged());
  }
}
