# SyncProgramReview
Program written to Sync result set/table from a data warehouse to an email automation tool (Vbout).

**Project Requirements:**

1) We need to develop a program to sync the tables of a data warehouse to Vbout, which is a marketing automation tool. 
2) We have RDBMS based tables in our data warehouse whereas Vbout supports a flat list of contacts. 
3) A Journey is a business term that can be technically represented, as a result, fetched from a SQL query.
4) A table can be used to generate multiple Journeys and a Journey can be made up of multiple tables. 
5) Basically a result set/result table is a journey. It can have joins of multiple tables / nested queries etc.
6) We need to create a list in Vbout for every Journey. And every record of a Journey needs to be pushed to the Vbout list.
7) As a Journey is the result of a SQL query, it has many columns. The Vbout list has fields for every column of the Journey.
8) In a nutshell, we need to appropriately fetch the records of the journey with all the fields and push those to the Vbout List.
9) The program also needs to run every night to find new/updated records in the journey and sync it to the Vbout list.
10) Some Journeys may have around a Million Records.
11) There can be a lot of Journeys.


**Tables:**


create table if not exists Journey(
  Journey_ID text Primary Key,
  Journey_Name text not null,
  Vbout_List_ID text unique not null,
  Vbout_List_Name text not null,
  Entry_SQL_Query text not null,
  Exit_SQL_Query text,
  Date_Created timestamp with time zone default now(),
  Date_Last_Modified timestamp with time zone default now()
);

create table if not exists Vbout_Field_Mapping(
  Mapping_ID serial Primary key,
  Vbout_List_ID text references Journey(Vbout_List_ID) not null,
  Vbout_Field_ID text unique not null,
  Data_Warehouse_Table_Column_Name text not null,
  isReal text default 'yes',
  Date_Created timestamp with time zone default now(),
  Date_Last_Modified timestamp with time zone default now()
);

create table if not exists Vbout_Journey_Sync_History(
  Sync_ID serial Primary key,
  Journey_ID text references Journey(Journey_ID) not null,
  Total_Rows_Calculated Integer not null,
  Total_Rows_Synced Integer not null,
  Sync_Start_Time timestamp with time zone default now(),
  Sync_End_Time timestamp with time zone default now(),
  Time_Last_Synced timestamp with time zone default now()
);

create table if not exists Triggers(
  id serial Primary key,
  Journey_ID text references Journey(Journey_ID) not null,
  trigger_data text not null
);

//For custom business logic

create table if not exists RecordType(
  id text Primary key,
  name text
);




**Setup Process:**

1) Create 5 custom tables in Postgresql in the ‘vboutsync' database. The queries for table creation are given above.
2) Insert data into our custom tables.
    
    a) Populate the 'Journey' Table
    
      i) Fill the Journey details, like ‘Journey_ID’ and ‘Journey_Name’.
        
      ii) Fill the Vbout list details created for this particular Journey, like ‘Vbout_List_ID’ and ‘Vbout_List_Name’.
        
      iii) Provide SQL queries. ‘Entry_SQL_Query’ has conditions that need to be satisfied by data to be added in a particular List. And ‘Exit_SQL_Query’  has conditions that need to be satisfied by data to be removed from a particular List.
        
      iv) We are using only ‘Entry_SQL_Query’ for this version.
        
      v)  Write the SQL query with the filters, joins, and other conditions for that journey. The query only has the ‘FROM’ and ‘WHERE’ part.
         
    
    b) Populate the ‘Vbout_Field_Mapping’ Table
        
      i) ‘Mapping_ID’ is the primary key, which is auto-increment.
      
      ii) ‘Vbout_List_ID’ is the ID of the list in Vbout and a foreign key to the Journey table and ‘Vbout_Field_ID’ is the id of a field in the Vbout list.
      
      iii) ‘Data_Warehouse_Table_Column_Name’ is the name of the column in the data warehouse.
       
      iv) ‘isReal’ is ‘yes’ when this column is available in the data warehouse and ‘no’ when a related column is available. If isReal is ‘no’, we handle these type of fields in the Custom Filter that we have created. 

3) Pack the project into a jar.

4) Schedule this jar in the client system to run at midnight and also pass the Journey ID as command line argument. 

**Sync Algorithm:**

1) The program runs at a scheduled time with the Journey ID as a parameter.
2) Based on the parameter, a journey is selected to be synced.
3) The configuration of the program is loaded (API keys, Thread Limits, API Calls per limits, etc).
4) The journey details are fetched from the Journey Table using MyBatis based on the parameter provided.
5) The program counts the number of records to be synced for the Journey based on the ‘last sync date’. Most of the Journey requires the data of more than one table. The program refers to the ‘Triggers’ table to decide from which table the ‘last sync date’ is going to be used.
6) Then the total count is divided into ranges (Number of the range is the number of DB threads). Eg. If there are 100 records and needs to be divided into 5 ranges: (1-20, 21-40, 41-60, 61-80, 81-100).
7) A Queue is created for JSON Objects using ActiveMQ.
8) Based on the number of ranges, a thread is created for each range that fetches the record from the table based on the SQL query in the ‘Journey’ Table and limits the records by using LIMIT & OFFSET using the range start and end.
    
    a) The ‘Journey’ Table ‘Entry_SQL_Query’ column has only ‘FROM’ and ‘WHERE’ part of the query, the ‘SELECT’ part is created based on the fields from the column ‘Data_Warehouse_Table_Column_Name’ of ‘Vbout_Field_Mapping’ table.
9) Every thread creates JSON objects for every row in the result and pushes the object into the queue created above. Multiple threads will help to speed up the fetch from the table process and act like pagination.
10) For fetching the data from our custom tables, we are using the ‘MyBatis’ ORM and for the Data warehouse that is the source table for the sync we are using the ‘JDBC’. 
    
    a) We are not using ORM for Data Warehouse because the data is dynamic. 
    
    b) If at some point some columns of data are added or removed from the warehouse, then we have to modify the program(Beans) and deploy it again.
    
    c) By using JDBC If some columns of data are added or removed from the warehouse, we only have to add or remove some fields in the ‘Vbout_Field_Mapping’ table or utmost change query in  ‘Journey’ Table.
11) Some threads are also created for fetching the JSON Objects from the ActiveMQ Queue and posting it into the Vbout API.
    a) This thread fetches objects from the queue.
    
    b) It finds the number of fields in the object.
    
    c) It then finds the mapping Vbout Field UID from the Vbout Field Mappings table.
    
    d) It then creates an Http Request with the parameters and posts the object to Vbout.
    
    e) Multiple threads will help to speed up the API Calls.
12) After the sync is complete, we update the Vbout Journey Sync History table.
