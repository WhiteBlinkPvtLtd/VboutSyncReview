package com.whiteblink.utils;

import com.whiteblink.beans.RecordType;
import com.whiteblink.beans.Vbout_Field_Mapping;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;


/*
 * Filter is a feature to support custom logic.
 * Currently it is used to add record_type_id to the objects if there exist a record_type_name field.
 * Any custom business logic for the program will be implemented using this class.
 * */
public class Filter {

    final JSONObject jsonRecordType;
    final JSONObject jsonFieldMapping;
    static final String RECORDTYPENAME = "case.\"recordtype.name\"";
    static final String RECORDTYPEID = "case.\"recordtype.id\"";


    public Filter(List<RecordType> recordTypes, List<Vbout_Field_Mapping> vboutFieldMappings) throws JSONException {
        jsonRecordType = new JSONObject();
        for (RecordType recordType : recordTypes) {
            jsonRecordType.put(recordType.getName(), recordType.getId());
        }

        jsonFieldMapping = new JSONObject();

        for (Vbout_Field_Mapping vbout_field_mapping : vboutFieldMappings) {
            jsonFieldMapping.put(vbout_field_mapping.getData_Warehouse_Table_Column_Name(), vbout_field_mapping.getVbout_Field_ID());
        }

    }

    public JSONObject doFilter(JSONObject filterObject) throws JSONException {
        if (jsonFieldMapping.has(RECORDTYPENAME) && filterObject.has(jsonFieldMapping.getString(RECORDTYPENAME))) {
            filterObject.put(jsonFieldMapping.getString(RECORDTYPEID), jsonRecordType.get(filterObject.getString(jsonFieldMapping.getString(RECORDTYPENAME))));
        }
        return filterObject;
    }
}
