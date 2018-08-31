# intact-portal-indexer
Pipeline to create the Solr indexes for IntAct Portal Search by taking data from graph db (neo4j)

Creates Following indexes:

* Interactor Index
* Non Clustered Interaction Index

## Prerequisites

1. You would need running solr 7.3.1 with cores created (Details below)
2. [jdk 1.8](http://www.oracle.com/technetwork/java/javase/downloads/index.html)
3. Embedded Graph db : Right now embedded at intact-portal-indexer/src/main/resources and comes along with git pull 


### Steps for running solr at your local:
```
1. Download solr 7.3.1(http://archive.apache.org/dist/lucene/solr/7.3.1/)
2. Uncompress the solr-7.3.1 zip or tar
3.Through command line go to root directory i.e solr-7.3.1
4.Create a solr home directory in your machine for eg. at /home/user/solr/solr-home
5.Execute : bin/solr start -s /home/user/solr/solr-home
6.Create Cores interactions and interactors as follows:-
Execute : bin/solr create_core -c interactions
Execute : bin/solr create_core -c interactors
7.Add following enteries to your /home/user/solr/solr-home/interactions/conf/managed-schema and /home/user/solr/solr-home/interactors/conf/managed-schema
*<field name="text" type="text_en_splitting" multiValued="true" indexed="true" required="true" stored="false"/>
*only if not present already : <field name="default" type="text_general" multiValued="true" indexed="true" stored="true"/>
*<copyField source="*" dest="text"/>
*Configure this as per your needs <copyField source="*" dest="default"/>
8.Restart solr
*Execute: bin/solr stop
*Execute: bin/solr start -s /home/user/solr/solr-home
9.Solr should be up and running at http://localhost:8983/solr
```

### Steps for pointing the application to an already running instance of solr for eg. http://example/solr
```
1. Open intact-portal-indexer/src/main/resources/application.properties
*Update 'spring.data.solr.host' property with your running instance of solr 'http://example/solr'
```
## Quickstart

```
1. cd intact-portal-indexer
2. mvn clean compile
3. Open intact-portal-indexer/src/main/resources/application.properties
*Uncomment line and specify the jobs you want to run : spring.batch.job.names=interactorIndexerJob,interactionIndexerJob
*Set spring.batch.job.enabled=true
4. Run intact-portal-indexer/src/main/java/uk/ac/ebi/intact/portal/indexer/IntactPortalIndexerApplication.java 
5. Check logs, When you see message like 'Indexing complete.'. Check in your solr intance if index is created
```
 
## Running the tests

```
1. Open intact-portal-indexer/src/main/resources/application.properties
*Set spring.batch.job.enabled=false  
2. Execute : mvn test

```

