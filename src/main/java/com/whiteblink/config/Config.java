package com.whiteblink.config;

import lombok.Data;
import lombok.Getter;

 /*
 This class consists of Default configuration options for the Sync Program
 and the JDBC connection credentials.
  */
@Data
public class Config {

    //Credentials For database connection
    @Getter
    private static final String DBNAME = "#";
    @Getter
    private static final String USERNAME = "#";
    @Getter
    private static final String PASSWORD = "#";
    @Getter
    private static final String HOST = "#";

    //Number Of Threads Used To Post Data To Vbout
    @Getter
    private static final int VBOUTTHREAD = 15;

    //Number of Threads Used To Fetch Data From Database
    @Getter
    private static final int DBTHREAD = 5;

    //Guava Rate Limiter Limit To Check Number Of API Calls Per Second
    @Getter
    private static final int GUAVALIMIT = 5;

    //API Key For Vbout
    @Getter
    private static final String APIKEY="#";

    //Private constructor to prevent Object creation for this class
    private Config() {}

}
