# SyncProgramReview
Program written to Sync result set/table from a data warehouse to an email automation tool (Vbout).

Project Requirements:

1) We need to develop a program to sync the tables of a data warehouse to Vbout, which is a marketing automation tool. 
2) We have RDBMS based tables in our data warehouse whereas Vbout supports a flat list of contacts. 
3) A Journey is a business term that can be technically represented as a result fetched from a SQL query.
4) A table can be used to generate multiple Journeys and a Journey can be made up of multiple tables. 
5) Basically a result set / result table is a journey. It can have joins of multiple tables / nested queries etc.
6) We need to create a list in Vbout for every Journey. And every record of a Journey needs to be pushed to the Vbout list.
7) As a Journey is the result of a SQL query, it has many columns. The Vbout list has fields for every column of the Journey.
8) In a nutshell, we need to appropriately fetch the records of the journey with all the fields and push those to the Vbout List.
9) The program also needs to run every night to find new / updated records in the journey and sync it to the Vbout list.
10) Some Journeys may have around a Million Records.
11) There can be a lot of Journeys.
