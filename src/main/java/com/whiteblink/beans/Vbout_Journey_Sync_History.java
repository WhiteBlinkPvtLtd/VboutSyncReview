package com.whiteblink.beans;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class Vbout_Journey_Sync_History {
    private int sync_ID;
    private String journey_ID;
    private int total_Rows_Calculated;
    private int total_Rows_Synced;
    private String sync_Start_Time;
    private String sync_End_Time;
    private String time_Last_Synced;
}
