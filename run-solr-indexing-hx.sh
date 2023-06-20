#!/bin/bash

export JAVA_HOME="/hps/software/users/hhe/intact/third-party-softwares/latest_intact_jdk11"
export PATH="${JAVA_HOME}/bin:$PATH"

mvn spring-boot:run -s settings.xml -Pip-solr-indexing-hx -Dmaven.repo.local=repository
