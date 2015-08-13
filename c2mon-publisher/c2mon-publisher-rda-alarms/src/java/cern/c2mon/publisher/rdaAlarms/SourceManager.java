/**
 * Copyright (c) 2015 European Organisation for Nuclear Research (CERN), All Rights Reserved.
 */

package cern.c2mon.publisher.rdaAlarms;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jmx.export.annotation.ManagedAttribute;
import org.springframework.jmx.export.annotation.ManagedResource;

import cern.c2mon.shared.client.alarm.AlarmValue;
import cern.cmw.data.Data;
import cern.cmw.data.DataFactory;
import cern.cmw.rda3.common.data.AcquiredData;

@ManagedResource(objectName = "cern.c2mon.publisher.rdaAlarms:name=SourceManager", description = "Alarm Soure Manager")
public class SourceManager extends TimerTask {

    public static final long FREQ = 15 * 60 * 1000; // run once per 15mn
    
    private static final Logger LOG = LoggerFactory.getLogger(SourceManager.class);
    
    private ConcurrentHashMap<String, String> alarmEquip = new ConcurrentHashMap<String, String>();
    private Map<String, RdaAlarmsProperty> properties = new HashMap<String, RdaAlarmsProperty>();    
    private AcquiredData sources;

    private int sourceCount;
    
    private DataProviderIntf dpi;
    private Timer timer;
    
    //
    // --- CONSTRUCTION -----------------------------------------------------------------------
    //
    public SourceManager() {
        timer = new Timer();
        timer.scheduleAtFixedRate(this, FREQ, FREQ);
    }
    
    // property setter used by the Spring config
    public void setDataProvider(DataProviderIntf dpi) {
        this.dpi = dpi;        
    }

    //
    // --- JMX --------------------------------------------------------------------------------
    //    
    @ManagedAttribute
    public int getSourceCount() {
        return sourceCount;
    }

    @ManagedAttribute
    public int getAlarmCount() {
        return alarmEquip.size();
    }

    //
    // --- PUBLIC METHODS ---------------------------------------------------------------------
    //    
    /**
     * @return <code>AcquiredData</code> for RDA publishing 
     */
    AcquiredData getSources() {
        return sources;
    }
        
    /**
     * To be called when application closes to properly release resources.
     */
    public void close() {
        dpi.close();
        timer.cancel();
    }

    public String getSourceNameForAlarm(String alarmId) {
        return alarmEquip.get(alarmId);
    }
    
    /**
     * The method allows the RdaAlarmsPublisher to find the property corresponding to a given
     * alarm. The properties for all sources are supposed to exist, because they are either
     * created on startup or during periodic update.
     * 
     * @param alarmId <code>String</code> alarm identifier (triplet)
     * @return <code>RdaAlarmPropery</code> providing all known alarms for the source of the alarm
     */
    public RdaAlarmsProperty findProp(String alarmId) {
        String sourceName = alarmEquip.get(alarmId);
        if (sourceName != null) {
            return properties.get(sourceName);
        } else {
            try {
                sourceName = dpi.getSource(alarmId);
                if (sourceName != null) {
                    alarmEquip.put(alarmId, sourceName);
                    RdaAlarmsProperty sourceProp = properties.get(sourceName);
                    if (sourceProp != null) {
                        return sourceProp;
                    } else {
                        return this.addSource(sourceName);
                    }
                }
            } catch (Exception e) {
                LOG.error("Call to dataprovider failed", e);
            }
        }
        return null;
    }
    

    /**
     * For efficiency: the method loads all source definitions and updates internal strucutres
     * accordingly. For the active alarms, the mapping to their source is also filled in. This
     * allows to use array calls on startup rather than a 1 by one query.
     * 
     * @param activeAlarms collection of active alarms
     * @throws Exception if the call to the dataprovider fails
     */
    public void initialize(Collection<AlarmValue> activeAlarms) throws Exception { 
        Data sd = DataFactory.createData();
        sources = new AcquiredData(sd);
        for (String source : dpi.getSourceNames()) {
            addSource(source);
        }        
        LOG.info("Declared {} sources.", sourceCount);
        
        // Use arraycall to init the alarm/source map
        Set<String> alarmIds = new HashSet<String>();
        if (activeAlarms != null) {
            for (AlarmValue av : activeAlarms) {    
                alarmIds.add(RdaAlarmsPublisher.getAlarmId(av));
            }
        }
        alarmEquip = dpi.initSourceMap(alarmIds);
    }
    
    
    //
    // --- PRIVATE METHODS ---------------------------------------------------------------------
    //
    private boolean exists(String sourceId) {
        return properties.containsKey(sourceId);
    }

    private RdaAlarmsProperty addSource(String source) {
        RdaAlarmsProperty property = new RdaAlarmsProperty(source);
        properties.put(source, property);        
        sources.getData().append(source, System.currentTimeMillis());
        sourceCount++;
        return property;
    }
    
    private void removeSource(String sourceId) {
        sources.getData().remove(sourceId);
        properties.remove(sourceId);
        
        Iterator<Map.Entry<String, String>> entries = alarmEquip.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<String, String> entry = entries.next();
            if (entry.getValue().equals(sourceId)) {
                entries.remove();
            }
        }        
        sourceCount--;
    }

    //
    // --- Implements TimerTask ----------------------------------------------------------------
    //
    @Override
    public void run() {
        LOG.info("Running source definition garbage collector ...");

        try {
            Collection<String> sourceDefs = dpi.getSourceNames();
            
            // 1. add new stuff
            for (String sourceId : sourceDefs) {
                if (!this.exists(sourceId)) {
                    addSource(sourceId);
                }
            }
            
            // 2. remove old stuff
            for (String sourceId : properties.keySet()) {
                if (!sourceDefs.contains(sourceId)) {
                    removeSource(sourceId);
                }
            }
            
        } catch (Exception e) {
            LOG.error("Failed to update list of sources", e);
        }                
        LOG.info("Declared {} sources.", sourceCount);
    }


}
