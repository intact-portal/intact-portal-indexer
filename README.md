# intact-portal-indexer
Pipeline to create the Solr indexes for IntAct Portal Search. 

Following indexes are created:

* Interactor Index
* Non Clustered Interaction Index

#Prerequisites

.You would need running solr 7.3.1 with cores created (Details below)
.http://www.oracle.com/technetwork/java/javase/downloads/index.html[jdk 1.8]


Steps for running solr at your local:
```
.http://archive.apache.org/dist/lucene/solr/7.3.1/[Download solr 7.3.1].
.Uncompress the solr-7.3.1 zip or tar
.Through command line go to root directory i.e solr-7.3.1
.Create a solr home directory in your machine for eg. at /home/user/solr/solr-home
.Execute : bin/solr start -s /home/user/solr/solr-home
.Create Cores interactions and interactors as follows:-
Execute : bin/solr create_core -c interactions
Execute : bin/solr create_core -c interactors
.Add following enteries to your /home/user/solr/solr-home/interactions/conf/managed-schema and /home/user/solr/solr-home/interactors/conf/managed-schema
<field name="text" type="text_en_splitting" multiValued="true" indexed="true" required="true" stored="false"/>
only if not present already : <field name="default" type="text_general" multiValued="true" indexed="true" stored="true"/>
<copyField source="*" dest="text"/>
Configure this as per your needs <copyField source="*" dest="default"/>
.Restart solr
Execute: bin/solr stop
Execute: bin/solr start -s /home/user/solr/solr-home
.Solr should be up and running at http://localhost:8983/solr
```

Steps for pointing the application to an already running instance of solr for eg. http://example/solr
```
.Open intact-portal-indexer/src/main/resources/application.properties
Update 'spring.data.solr.host' property with your running instance of solr 'http://example/solr'
```
# Quickstart

```
. cd intact-portal-indexer
. mvn clean compile
.Open intact-portal-indexer/src/main/resources/application.properties
Uncomment line and specify the jobs you want to run : spring.batch.job.names=interactorIndexerJob,interactionIndexerJob
Set spring.batch.job.enabled=true
```
 
## Running the tests

Execute : mvn test
 
. 
