package com.whiteblink.beans;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class Vbout_Field_Mapping {
    private int mapping_ID;
    private String vbout_List_ID;
    private String vbout_Field_ID;
    private String isReal;
    private String data_Warehouse_Table_Column_Name;
    private String date_Created;
    private String date_Last_Modified;
}

