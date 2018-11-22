# intact-portal-indexer
Pipeline to create the Solr indexes for IntAct Portal Search by taking data from graph db (neo4j)

Creates Following indexes:

* Interactor Index
* Non Clustered Interaction Index

## Prerequisites

1. Running instance of solr 7.3.1 with 'interactions'  and 'interactors' cores created (Details below)
2. [jdk 1.8](http://www.oracle.com/technetwork/java/javase/downloads/index.html)
3. Embedded Graph db(neo4j) : Have your own at 'intact-portal-indexer/src/main/resources' or use the one that comes along with git       pull in which case ignore the step 


### Steps for running solr at your local: ###
```
1. Download solr 7.3.1(http://archive.apache.org/dist/lucene/solr/7.3.1/)
2. Uncompress the solr-7.3.1 zip or tar
3. Through command line go to root directory i.e solr-7.3.1
4. Create a solr home directory in your machine for eg. at /home/user/solr/solr-home
5. Execute : bin/solr start -s /home/user/solr/solr-home
6. Create Cores interactions and interactors as follows:-
  a. Execute : bin/solr create_core -c interactions
  b. Execute : bin/solr create_core -c interactors
7. Add following enteries to your /home/user/solr/solr-home/interactions/conf/managed-schema and /home/user/solr/solr-home/interactors/conf/managed-schema
  a. <field name="text" type="text_en_splitting" multiValued="true" indexed="true" required="true" stored="false"/>
  b. only if not present already : <field name="default" type="text_general" multiValued="true" indexed="true" stored="true"/>
  c. <copyField source="*" dest="text"/>
  d. Configure this as per your needs <copyField source="*" dest="default"/>
8. Restart solr
  a. Execute: bin/solr stop
  b. Execute: bin/solr start -s /home/user/solr/solr-home
9. Solr should be up and running at http://localhost:8983/solr
```

### Have following maven profiles in your mvn settings.xml:
```
1.
   <!-- ================================================================ -->
   <!--                        Solr Indexer Profile Local Unit Test                     -->
   <!-- ================================================================ -->
           <profile>
               <id>ip-solr-indexing-local-unittest</id>
               <properties>
                   <graphdb.source>file:@project.basedir@/src/main/resources/graph.db</graphdb.source>
                   <solr.server>http://localhost:8983/solr</solr.server>
                   <bj.enabled>false</bj.enabled>
               </properties>
           </profile>
           
 2.
    <!-- ================================================================ -->
    <!--                        Solr Indexer Profile Local                     -->
    <!-- ================================================================ -->
            <profile>
                <id>ip-solr-indexing-local</id>
                <properties>
                    <graphdb.source>file:@project.basedir@/src/main/resources/graph.db</graphdb.source>
                    <solr.server>http://localhost:8983/solr</solr.server>
                    <bj.enabled>true</bj.enabled>
                </properties>
            </profile>

3. For internal developers there is one more profile called "ip-solr-indexing-hx", please ask a intact developer for the same

    
```

## Quickstart

```
1. cd intact-portal-indexer
2. mvn clean compile
3. Run intact-portal-indexer/src/main/java/uk/ac/ebi/intact/portal/indexer/IntactPortalIndexerApplication.java with maven profile 
   ip-solr-indexing-local (given above)
4. Check logs, When you see message like 'Indexing complete.'. Check in your solr instance if index is created
```
 
## Running the tests

```
1. Execute : mvn -Pip-solr-indexing-local-unittest test

```

