package com.whiteblink.core;

import com.google.common.util.concurrent.RateLimiter;
import com.whiteblink.activemq.ActiveMqHandler;
import com.whiteblink.beans.*;
import com.whiteblink.config.Config;
import com.whiteblink.database.DBMapping;
import com.whiteblink.utils.Filter;
import com.whiteblink.utils.Range;
import com.whiteblink.utils.SQLGenerator;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.json.JSONException;
import org.json.JSONObject;

import javax.jms.JMSException;
import java.io.IOException;
import java.io.Reader;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/*
This class is the core of the program.
1) Accepts the sync parameter(Journey ID) from the App class.
2) Based on the Journey ID, the Journey details are loaded from the VboutSync database using Mybatis.
3) This class initializes a filter that handles Custom Business Logic based on the journey selected.
4) This class has a Start function that is responsible for starting the sync.
5) When the start function is called, it does the following tasks:
    a) Initializes the sync by creating a JDBC connection to the database that needs to be synced,
        Creates a Queue using ActiveMq, counts the total number of records to be synced by running
        a count SQL query on the journey and then divides the count into ranges.
    b) Starts the Database job by alloting every range to a thread that fetches the records for the
        journey and then creates a Json object for each records and then adds the Json object to the
        queue implemented using ActiveMQ.
    c) Starts the Vbout Job by creating multiple threads that are responsible for collecting Json Objects
        from the queue and posting them into Vbout API using Apache Http Client.
    d)  Starts the destructor thread which is responsible for terminating the Sync and all the threads
        based on 2 conditions:
        i) If all the records are posted to the API and 200 Ok response is recieved for every response.
        ii) If there is 10 seconds of inactivity in the Vbout Thread.
6) After the sync is over, the destructor prints the details about the Sync and also logs the sync history
    in our database.
 */

public class VboutSync {


    final String journeyId;
    String preparedSyncQuery = null;
    String preparedCountQuery = null;

    int totalRowsToBeUpdated = 0;
    int totalRowsAddedToQueue = 0;
    int totalRowsProcessed = 0;
    int totalRowsUpdated = 0;

    long timeoutActivity = 0;

    Journey journey;
    DBMapping dbMapping;
    Filter filter;
    ActiveMqHandler activeMqHandler;

    java.util.Date startTime;
    Connection connection;
    RateLimiter limiter;
    SqlSession vboutSession;
    Logger logger;

    List<Vbout_Field_Mapping> vboutFieldMappings;
    List<Vbout_Field_Mapping> vboutFieldFakeMappings;
    List<Vbout_Journey_Sync_History> vboutJourneySyncHistories;
    List<Triggers> triggers;
    List<RecordType> recordTypes;
    List<Range> ranges;


    //Constructor that accepts the journey id and initializes the sync
    public VboutSync(String journeyId,Logger logger) throws IOException, JSONException {
        this.journeyId = journeyId;
        this.logger=logger;

        //Initializing the 10 seconds timeout with the current time.
        timeoutActivity=System.currentTimeMillis();

        //Loading Journey details from the database
        loadJourneyDetails();
        logger.log(Level.ALL,"Journey Details Loaded.");

        //Preparing Queries
        SQLGenerator sqlGenerator = new SQLGenerator(journey, vboutFieldMappings, vboutJourneySyncHistories, triggers);
        preparedSyncQuery = sqlGenerator.getPreparedSyncQuery();
        logger.log(Level.ALL,preparedSyncQuery);
        preparedCountQuery = sqlGenerator.getPreparedCountQuery();
        logger.log(Level.ALL,"Queries Prepared");
        logger.log(Level.ALL,"Ready to Sync.");

        //We set the API Call Limit Rate using Guava rate limiter
        limiter = RateLimiter.create(Config.getGUAVALIMIT());
    }

    //This function loads the Journey details from our database using Mybatis
    private void loadJourneyDetails() throws JSONException, IOException {
        Reader vboutReader = Resources.getResourceAsReader("VboutSyncConfig.xml");
        SqlSessionFactory vboutSessionFactory = new SqlSessionFactoryBuilder().build(vboutReader);
        vboutSession = vboutSessionFactory.openSession();
        vboutSession.getConfiguration().addMapper(DBMapping.class);
        dbMapping = vboutSession.getMapper(DBMapping.class);
        journey = dbMapping.getJourney(journeyId);
        vboutFieldMappings = dbMapping.getVboutFieldMappingsYes(journey.getVbout_List_ID());
        vboutFieldFakeMappings = dbMapping.getVboutFieldMappingsAll(journey.getVbout_List_ID());
        vboutJourneySyncHistories = dbMapping.getVboutJourneySyncHistory(journeyId);
        triggers = dbMapping.getTriggers(journeyId);
        recordTypes = dbMapping.getRecordType();
        vboutSession.commit();
        //Initialize filter from journey details
        filter=new Filter(recordTypes,vboutFieldFakeMappings);
    }

    //This funcion triggers the Sync
    public void start() throws JMSException, SQLException {
        //Sync start time recorded for logging to the Sync History
        startTime=new java.util.Date();

        initializeSync();
        startDatabaseJob();
        startVboutJob();
        destructor();
    }

    /*
    This function initializes the sync by creating a JDBC connection to the Database
    that needs to be synced, counts the total records to be synced, divides the total
    number of records into ranges and also creates a queue using ActiveMQ
     */
    private void initializeSync() throws SQLException, JMSException {
        //Connecting to the database that needs to be synced
        connection = DriverManager.getConnection("jdbc:postgresql://" + Config.getHOST() + "/" + Config.getDBNAME(), Config.getUSERNAME(), Config.getPASSWORD());
        connection.setAutoCommit(false);
        //Counting the total number of records to be synced
        count();
        //Dividing the total number of records into ranges
        ranges = Range.getRanges(totalRowsToBeUpdated, Config.getDBTHREAD());
        //Creating a Queue using ActiveMQ
        activeMqHandler = new ActiveMqHandler(journeyId + "_" + (new java.util.Date().getTime()));
    }

    /*
    This function counts the total number of records to be synced.
     */
    private void count() throws SQLException {
        try(Statement st = connection.createStatement()) {
            st.setFetchSize(100);
            try(ResultSet rs = st.executeQuery(preparedCountQuery)){
                logger.log(Level.ALL,"Running Count Query");
                while (rs.next()) {
                    totalRowsToBeUpdated = rs.getInt(1);
                }
            }
            logger.log(Level.ALL,"Total Count: {0}",totalRowsToBeUpdated);
        }
    }

    //This funtion creates a thread for each range (Pagination)
    public void startDatabaseJob() {
        for (final Range range : ranges) {
            Runnable r1 = () -> dbThread(range);
            new Thread(r1).start();
        }
    }

    /*
     * This Method Fetches The Records From The Table And Creates Json Objects from the records.
     * It then inserts the Json Objects in the Queue
     * */
    public void dbThread(Range range) {
        try (PreparedStatement preparedStatement = connection.prepareStatement(preparedSyncQuery + " LIMIT ? OFFSET ? ;");){
            logger.log(Level.ALL,"Sync Running for {0} : {1}" ,new Object[] {range.getFrom(), range.getTo()});
            preparedStatement.setInt(1, (range.getTo() - range.getFrom()) + 1);
            preparedStatement.setInt(2, range.getFrom() - 1);

            try(ResultSet rs = preparedStatement.executeQuery()){
                while (rs.next()) {
                    //Converting each row into Json Objects
                    JSONObject jsonObject = new JSONObject();
                    for (Vbout_Field_Mapping vbout_field_mapping : vboutFieldMappings) {
                        jsonObject.put(vbout_field_mapping.getVbout_Field_ID(), rs.getString(vbout_field_mapping.getData_Warehouse_Table_Column_Name()));
                    }

                    /*
                    The Json object is passed through a filter that does modifications
                    to the Json objects if any Custom Business logic exists.
                     */
                    jsonObject=filter.doFilter(jsonObject);

                    //Adding the Json object to the queue
                    logger.log(Level.ALL,"Adding Record to Queue : {0}",jsonObject);
                    activeMqHandler.messageSender(jsonObject);

                    //Incrementing the count of total Json Objects added
                    totalRowsAddedToQueue++;
                }
            }
        } catch (SQLException | JSONException | JMSException e) {
            logger.log(Level.ALL,"DB Thread Crashed Due To The Following Exception: {0}",e.toString());
        }
    }

    /*
    This function creates multiple threads that would fetch Json objects from the queue
    and post it to the Vbout API. The number of threads depends on the Config class.
     */
    public void startVboutJob() {
        Runnable r1 = this::vboutThread;
        for (int i = 0; i < Config.getVBOUTTHREAD(); i++) {
            new Thread(r1).start();
        }
    }


    /*
    This Method Fetches Json Objects from the queue and posts it to the Vbout API
    using Apache http client.
     */
    public void vboutThread() {
        //This loop will run until all the contacts are processed
        while (totalRowsToBeUpdated!=totalRowsUpdated) {
            try(CloseableHttpClient httpclient = HttpClients.createDefault()){

                //Dequeue a Json object from the queue to be processed
                JSONObject vboutContact = activeMqHandler.messageReceiver();
                logger.log(Level.ALL,"Processing Vbout Contact : {0}", vboutContact.getString("email"));

                //Creating a HTTP request
                HttpPost httppost = new HttpPost("https://api.vbout.com/1/emailmarketing/synccontact.json?key=" + Config.getAPIKEY());

                List<NameValuePair> params = new ArrayList<>();
                params.add(new BasicNameValuePair("status", "Active"));
                params.add(new BasicNameValuePair("listid", journey.getVbout_List_ID()));
                for (Vbout_Field_Mapping vbout_field_mapping : vboutFieldMappings) {
                    params.add(new BasicNameValuePair(vbout_field_mapping.getVbout_Field_ID(), vboutContact.getString(vbout_field_mapping.getVbout_Field_ID())));
                }

                httppost.setEntity(new UrlEncodedFormEntity(params));

                //Using Guava Rate Limiter To Limit the API call rate per second
                limiter.acquire();

                //Executing the request and fetching the response
                HttpResponse response = httpclient.execute(httppost);

                //Incrementing the total rows processed
                totalRowsProcessed++;

                //Checking if the call was successful
                if (response.getStatusLine().getStatusCode() == 200) {
                    //Incrementing the counter for every success calls
                    totalRowsUpdated++;
                }

                //Logging the response from the Vbout API
                StringBuilder requestStringBuilder=new StringBuilder();
                requestStringBuilder.append(httppost.toString()).append("\n");
                for (NameValuePair basicNVP:params) {
                    requestStringBuilder.append("\n").append(basicNVP.getName()).append(" : ").append(basicNVP.getValue());
                }
                HttpEntity entity = response.getEntity();
                String responseString = EntityUtils.toString(entity, "UTF-8");
                logger.log(Level.ALL,"{0} {1} {2}",new Object[]{requestStringBuilder,response,responseString});
                logger.log(Level.ALL,"Progress : ({0} | {1}) / {2} " , new Object[]{totalRowsProcessed, totalRowsUpdated , totalRowsToBeUpdated});

                //Updating the 10 seconds inactivity param
                timeoutActivity=System.currentTimeMillis();

            } catch (JMSException | JSONException | NullPointerException e) {
                //We ignore if there is an exception with one of the Json Request. We proceed with the next request.
            }catch (IOException e){
                logger.log(Level.ALL, e.toString());
            }
        }
    }


    /*
    This function is responsible for terminating the program when sync is complete
    based on 2 params:
    1) All the rows have been processed
    2) There is a 10 seconds inactivity in the Vbout Post Thread.
     */
    public void destructor()
    {
        try{
            //Checking if all the rows are processed
            while (totalRowsProcessed!=totalRowsToBeUpdated)
            {
                //Checking if there is a 10 seconds inactivity
                long currentTime=System.currentTimeMillis();
                long timeDiff=currentTime-timeoutActivity;
                long secondsElapsed=timeDiff/ 1000L;
                if(secondsElapsed> 10L)
                {
                    break;
                }
            }

            //If the control reaches here, it means the program should now terminate!

            activeMqHandler.close();
            connection.close();

            logger.log(Level.ALL,"Total Rows To Be Synced: {0}",totalRowsToBeUpdated);
            logger.log(Level.ALL,"Total Synced Contacts: {0}",totalRowsUpdated);
            logger.log(Level.ALL,"Total Contacts Processed: {0}",totalRowsProcessed);
            logger.log(Level.ALL,"Total Sync Failed: {0}",(totalRowsToBeUpdated-totalRowsUpdated));

            //If all the rows are updated with 200 OK response, we add this sync in the history
            if(totalRowsToBeUpdated==totalRowsUpdated)
            {
                Vbout_Journey_Sync_History vboutJourneySyncHistory=new Vbout_Journey_Sync_History();
                vboutJourneySyncHistory.setJourney_ID(journeyId);
                vboutJourneySyncHistory.setSync_Start_Time(startTime.toString());
                vboutJourneySyncHistory.setSync_End_Time(new java.util.Date().toString());
                vboutJourneySyncHistory.setTime_Last_Synced(startTime.toString());
                vboutJourneySyncHistory.setTotal_Rows_Calculated(totalRowsToBeUpdated);
                vboutJourneySyncHistory.setTotal_Rows_Synced(totalRowsUpdated);
                dbMapping.addVboutJourneySyncHistory(vboutJourneySyncHistory);
            }else {
                /*
                If all the records did not fetch 200 Ok, we log the request
                but we set the last sync time as the previous last sync time so that this sync
                is run again the next day.
                */
                if(!vboutJourneySyncHistories.isEmpty())
                {
                    Vbout_Journey_Sync_History vboutJourneySyncHistory=new Vbout_Journey_Sync_History();
                    vboutJourneySyncHistory.setJourney_ID(journeyId);
                    vboutJourneySyncHistory.setSync_Start_Time(startTime.toString());
                    vboutJourneySyncHistory.setSync_End_Time(new java.util.Date().toString());
                    vboutJourneySyncHistory.setTime_Last_Synced(vboutJourneySyncHistories.get(0).getTime_Last_Synced());
                    vboutJourneySyncHistory.setTotal_Rows_Calculated(totalRowsToBeUpdated);
                    vboutJourneySyncHistory.setTotal_Rows_Synced(totalRowsUpdated);
                    dbMapping.addVboutJourneySyncHistory(vboutJourneySyncHistory);
                }
            }
            vboutSession.commit();
            vboutSession.close();
            System.exit(0);
        }catch (Exception e){
            logger.log(Level.ALL,"Could Not Destroy Session Due To Following Reasons: {0}",e.toString());
        }
    }


}
