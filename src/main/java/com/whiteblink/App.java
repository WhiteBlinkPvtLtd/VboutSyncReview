package com.whiteblink;


import com.whiteblink.core.VboutSync;
import org.json.JSONException;

import javax.jms.JMSException;
import java.io.IOException;
import java.sql.SQLException;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

/*
This is the Main class that uses the VboutSync class to perform Sync.
 */
public class App {
    /*
    This main function accepts a command line argument that is the Journey ID
    and performs the sync for that journey ID
     */
    public static void main( String[] args ){

        //fetching the journey ID
        String journeyID=args[0];

        //Setting up logger to print logs
        Logger logger=Logger.getLogger(App.class.getName());
        Handler handlerObj = new ConsoleHandler();
        handlerObj.setLevel(Level.ALL);
        logger.addHandler(handlerObj);
        logger.setLevel(Level.ALL);
        logger.setUseParentHandlers(false);

        //Starting the Sync
        try{
            VboutSync vboutSync=new VboutSync(journeyID,logger);
            vboutSync.start();
        }catch (IOException | JMSException | JSONException | SQLException e){
            logger.log(Level.ALL,"Vbout Sync Failed Due To Following Reason: {0}",e.toString());
        }
    }

    //Private constructor to prevent Object creation for this class
    private App(){}
}
