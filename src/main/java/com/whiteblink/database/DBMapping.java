package com.whiteblink.database;

import com.whiteblink.beans.*;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

import java.util.List;


//SQL queries for MyBatis ORM
public interface DBMapping {

    @Select("SELECT * FROM Journey WHERE journey_ID = #{journeyId}")
     Journey getJourney(String journeyId);

    @Select("SELECT * FROM Vbout_Field_Mapping WHERE vbout_List_ID = #{vboutListID} and isReal = 'yes'" )
    List<Vbout_Field_Mapping> getVboutFieldMappingsYes(String vboutListID);

    @Select("SELECT * FROM Vbout_Field_Mapping WHERE vbout_List_ID = #{vboutListID}" )
     List<Vbout_Field_Mapping> getVboutFieldMappingsAll(String vboutListID);

    @Select("SELECT * FROM Vbout_Journey_Sync_History WHERE journey_ID = #{journeyId} order by Sync_ID desc")
    List<Vbout_Journey_Sync_History> getVboutJourneySyncHistory(String journeyId);

    @Insert("insert into Vbout_Journey_Sync_History values(DEFAULT, #{journey_ID}, #{total_Rows_Calculated}, #{total_Rows_Synced}, #{sync_Start_Time}, #{sync_End_Time}, #{time_Last_Synced})")
    @Options(useGeneratedKeys = true, keyProperty = "sync_ID")
    void addVboutJourneySyncHistory(Vbout_Journey_Sync_History vboutJourneySyncHistory);

    @Select("SELECT * FROM Triggers WHERE journey_ID = #{journeyId}")
    List<Triggers> getTriggers(String journeyId);

    @Select("SELECT * FROM RecordType")
    List<RecordType> getRecordType();

}
