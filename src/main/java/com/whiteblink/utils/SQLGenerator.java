package com.whiteblink.utils;

import com.whiteblink.beans.Journey;
import com.whiteblink.beans.Triggers;
import com.whiteblink.beans.Vbout_Field_Mapping;
import com.whiteblink.beans.Vbout_Journey_Sync_History;
import lombok.Data;
import lombok.Getter;

import java.util.List;

/*
* This class is used to generate sql queries for journey from the base sql query and the vbout field mappings.
* */
@Data
public class SQLGenerator {

    final Journey journey;
    final List<Vbout_Field_Mapping> vboutFieldMappings;
    final List<Vbout_Journey_Sync_History> vboutJourneySyncHistories;
    final List<Triggers> triggers;
    @Getter
    String preparedSyncQuery=null;
    @Getter
    String preparedCountQuery=null;

    //Constructor used to initialize the object and trigger query generation
    public SQLGenerator(Journey journey, List<Vbout_Field_Mapping> vboutFieldMappings, List<Vbout_Journey_Sync_History> vboutJourneySyncHistories, List<Triggers> triggers) {
        this.journey = journey;
        this.vboutFieldMappings = vboutFieldMappings;
        this.vboutJourneySyncHistories = vboutJourneySyncHistories;
        this.triggers = triggers;
        prepareSyncQuery();
        prepareCountQuery();
    }


    //This function prepares the SQL query to fetch data for the journey
    private void prepareSyncQuery()
    {
        StringBuilder syncQueryBuilder = new StringBuilder();
        String baseQuery=journey.getEntry_SQL_Query();
        syncQueryBuilder.append("Select");
        boolean firstLoop=true;

        for (Vbout_Field_Mapping vbout_field_mapping:vboutFieldMappings){
            if(firstLoop)
            {
                syncQueryBuilder.append(" ").append(vbout_field_mapping.getData_Warehouse_Table_Column_Name());
                firstLoop=false;
            }else {
                syncQueryBuilder.append(", ").append(vbout_field_mapping.getData_Warehouse_Table_Column_Name());
            }
        }

        syncQueryBuilder.append(" ").append(baseQuery);
        preparedSyncQuery=syncQueryBuilder.toString();

        if(!vboutJourneySyncHistories.isEmpty())
        {
            Vbout_Journey_Sync_History lastSyncHistory=vboutJourneySyncHistories.get(0);
            String lastSyncedDate=lastSyncHistory.getTime_Last_Synced();

            for (Triggers trigger: triggers) {
                syncQueryBuilder.append(" And (").append(trigger.getTrigger_data()).append(">='").append(lastSyncedDate).append("') ");
            }
            preparedSyncQuery=syncQueryBuilder.toString();

        }

    }

    //This function prepares the SQL query to count data for the journey
    private void prepareCountQuery()
    {
        StringBuilder countQueryBuilder=new StringBuilder();
        String baseQuery=journey.getEntry_SQL_Query();
        countQueryBuilder.append("Select count(").append(vboutFieldMappings.get(0).getData_Warehouse_Table_Column_Name()).append(") as count ");
        countQueryBuilder.append(" ").append(baseQuery);
        preparedCountQuery=countQueryBuilder.toString();

        if(!vboutJourneySyncHistories.isEmpty())
        {
            Vbout_Journey_Sync_History lastSyncHistory=vboutJourneySyncHistories.get(0);
            String lastSyncedDate=lastSyncHistory.getTime_Last_Synced();

            for (Triggers trigger: triggers) {
                countQueryBuilder.append(" And (").append(trigger.getTrigger_data()).append(">='").append(lastSyncedDate).append("') ");
            }
            preparedCountQuery=countQueryBuilder.toString();

        }
    }
}
