#!/bin/bash
#SBATCH --time=48:00:00   # walltime
#SBATCH --ntasks=1   # number of tasks
#SBATCH --cpus-per-task=4   # number of CPUs Per Task i.e if your code is multi-threaded
#SBATCH --nodes=1   # number of nodes
#SBATCH -p production   # partition(s)
#SBATCH --mem=64G   # memory per node
#SBATCH -J "PORTAL_INDEXING"   # job name
#SBATCH -o "/nfs/production/hhe/intact/data/solr-indexing-logs/portal-index-%j.out"   # job output file
#SBATCH --mail-user=intact-dev@ebi.ac.uk   # email address
#SBATCH --mail-type=ALL

export JAVA_HOME="/hps/software/users/hhe/intact/third-party-softwares/latest_intact_jdk11"
export PATH="${JAVA_HOME}/bin:$PATH"

mvn spring-boot:run -s settings.xml -Pip-solr-indexing-hx
