# Taxi application stack

## Getting started

I were interested to created something like Uber or Gett, so I decided 
create the same application for a college degree. 

### Overview
It contains [microservice](https://github.com/anonlatte/taxiGrpcService), 
which has been written in Go using gRPC, android
[driver's application](https://github.com/anonlatte/TaxiService/tree/master/drivers_app),
[user's application](https://github.com/anonlatte/TaxiService/tree/master/customers_app)
and primitive desktop [disptatcher's application](https://github.com/anonlatte/DispatcherApp) were written in Kotlin.
Desktop application (__dispactcher's app__) is using JavaFX12. 
MySQL was chosen as a database due to its comfortable data presentation.

## Quick start

### Requirements

- Deployed container with [microserivce](https://github.com/anonlatte/taxiGrpcService)
- Google cloud account for getting the API key

## Setting up

1. Clone [the repository](https://github.com/anonlatte/TaxiService) which contains source files of user's and driver's application.

2. Create ```keystore.properties``` in the project folder as follows:
``` 
GOOGLE_MAPS_API_KEY=
API_VERSION=v1
ServerAddress=
ServerPort=
```
**These applications are using Google maps API so follow the [instruction](https://developers.google.com/maps/documentation/embed/get-api-key) 
to find out how to get the api key.**

Api version is defined in the service's sources, server's address 
can be checked with ```ifconfig``` or ```ipconfig```, the port that 
you have defined.

## Building 

After all we can build and run the apps. 

## Media

- Customer application

![Customer application](https://i.imgur.com/PMOEN0N.png)
- Driver application

![Driver application](https://i.imgur.com/iBs5TJh.png) 

## Built with

- [gRPC](https://grpc.io/)

## Authors

- [Proshunin German](https://www.linkedin.com/in/anonlatte/)

See also the list of [contributors](https://github.com/anonlatte/TaxiService/graphs/contributors) who participated in this project.


## License

This project is licensed under GNU GENERAL PUBLIC LICENSE - see the [LICENSE](LICENSE) file for more details.