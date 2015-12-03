/**
 * Copyright (c) 2015 European Organisation for Nuclear Research (CERN), All Rights Reserved.
 */

package cern.c2mon.publisher.mobicall;

import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * For test purpose. Creates some alarm data and serves it to the test classes through the same
 * interface than the database-based production alarm provider.
 * 
 * Initially alarms FF:FM:1 and FF:FM:2 are present. On the second call (or first "reload"), the
 * alarm FF:FM:1 is replaced by FF:FM:3
 * 
 * @author mbuttner
 */
public class MobicallConfigLoaderMock implements MobicallConfigLoaderIntf {

    private static final Logger LOG = LoggerFactory.getLogger(MobicallConfigLoaderMock.class);
    
    private ConcurrentHashMap<String, MobicallAlarm> alarms;
    private long count = 0;
    
    //
    // --- CONSTRUCTION ------------------------------------------------------------------------------
    //
    public MobicallConfigLoaderMock() {
        alarms = new ConcurrentHashMap<String, MobicallAlarm>();
        
        MobicallAlarm ma1 = createAlarm("FF", "FM", 1, "111", "Dummy problem description");
        alarms.put("FF:FM:1", ma1);

        MobicallAlarm ma2 = createAlarm("FF", "FM", 2, "211", "Dummy problem description");
        alarms.put("FF:FM:2", ma2);
    }
    
    //
    // --- Implements MobicallConfigLoaderIntf -------------------------------------------------------
    // 
    @Override
    public void loadConfig() {
        LOG.info("Request for config load received");
        count++;
        
        // for test purposes: simulate a change in the configuration
        if (count == 2) {
            MobicallAlarm ma3 = createAlarm("FF", "FM", 3, "311", "Dummy problem description");
            alarms.put("FF:FM:3", ma3);
            alarms.remove("FF:FM:1");
        }
    }

    @Override
    public MobicallAlarm find(String alarmId) {
        return alarms.get(alarmId);
    }

    @Override
    public void close() {
        // No need for this in the mock
    }
    
    //
    // --- PUBLIC METHODS --------------------------------------------------------------------------
    //
    /**
     * @return <code>long</code> the number of times the configuration reload was triggered
     */
    public long getCount() {
        return this.count;
    }

    //
    // --- PRIVATE METHODS -------------------------------------------------------------------------
    //
    private static MobicallAlarm createAlarm(String sys, String id, int fc, String mobi, String pb) {
        MobicallAlarm ma = new MobicallAlarm(sys + ":" + id + ":" + fc);
        ma.setSystemName(sys);
        ma.setIdentifier(id);
        ma.setFaultCode(fc);
        ma.setNotificationId(mobi);
        ma.setProblemDescription(pb);
        return ma;
    }
    
}
