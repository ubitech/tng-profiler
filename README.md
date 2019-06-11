[![Build Status](https://jenkins.sonata-nfv.eu/buildStatus/icon?job=tng-api-gtw/master)](https://jenkins.sonata-nfv.eu/job/tng-profiler)
[![Join the chat at https://gitter.im/5gtango/tango-schema](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/sonata-nfv/5gtango-sdk)

# tng-analytics-engine component
This is a 5GTANGO component that aims to provide insights for the efficiency of the developed VNFs and NSs with regards to resources usage, elasticity efficiency and performance aspects. 

<p align="center"><img src="https://github.com/sonata-nfv/tng-api-gtw/wiki/images/sonata-5gtango-logo-500px.png" /></p>

### Documentation
For get informed about how the Analytics Engine is interacting with other 5GTango components and what kind of APIs it exposes, please visit the wiki page: https://github.com/sonata-nfv/tng-analytics-engine/wiki

### CI Integration
All pull requests are automatically tested by Jenkins and will only be accepted if no test is broken.

### License

This 5GTANGO component is published under Apache 2.0 license. Please see the LICENSE file for more details.

### Lead Developers

The following lead developers are responsible for this repository and have admin rights. They can, for example, merge pull requests.

- Eleni Fotopoulou ([@elfo](https://github.com/efotopoulou))
- Anastasios Zafeiropoulos ([@azafeiropoulos](https://github.com/azafeiropoulos))

### Feedback-Chanel

* Please use the GitHub issues to report bugs.








// run analytics engine as containers
//make sure select ENV MONGO_DB localhost at tng-analytics-engine dockerfile
sudo docker build --no-cache -t tng-analytics-engine  .
docker run  --name son-mongo --net host mongo
docker run --name tng-analytics-engine  --net host tng-analytics-engine  
docker run --name analyticserver --net host analyticserver


//run analytics engine via docker compose 
mvn -DPHYSIOG_URL='http://analyticserver' -DPROMETHEUS_URL='http://pre-int-vnv-bcn.5gtango.eu:9090' -DMONITORING_ENGINE='http://pre-int-vnv-bcn.5gtango.eu:8000' clean install

docker-compose build --no-cache tng-analytics-engine 
docker-compose up


