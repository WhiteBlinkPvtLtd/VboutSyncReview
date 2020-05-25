package com.whiteblink.beans;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class Journey {
    private String journey_ID;
    private String journey_Name;
    private String vbout_List_ID;
    private String vbout_List_Name;
    private String entry_SQL_Query;
    private String exit_SQL_Query;
    private String date_Created;
    private String date_Last_Modified;
}
