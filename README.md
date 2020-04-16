# Spring Cloud Gateway as Sidecar

This sample show how easy you can put on an existing `API` the `Spring Cloud Gateway` as kind of `SideCar` where you can manage your Security, Logging etc. 
Or just provide a other `Endpoint` `URL` like in this sample.

Let's create a Service with a Reactive Spring Boot Application and MongoDB and a Rest Endpoint.

We start with the `MongoDB` document class `Customer` and a `ReactiveCrudRepository` interface `CustomerRepository`
```kotlin
@Document
data class Customer(@Id val id: String = UUID.randomUUID().toString(), val name: String)
interface CustomerRepository : ReactiveCrudRepository<Customer, String>
```

Now let's also create a service class `CustomerService` for it where we provide the following functionality. 
* save
* findAll
* deleteAll
* findById

```kotlin
@Service
class CustomerService(private val customerRepository: CustomerRepository) {
    fun save(customer: Customer) = customerRepository.save(customer)
    fun findAll() = customerRepository.findAll()
    fun deleteAll() = customerRepository.deleteAll()
    fun findById(id: String) = customerRepository.findById(id)
}
```

That we have some data we create a little functionality on application start with the `ApplicationRunner` from Spring Boot.  
Let's create a Bean definitionfor the `ApplicationRunner` that delete first all entries and then save some sample values to it.
 
```kotlin
  runApplication<SidecarGatewayApplication>(*args) {
        addInitializers(
            beans {
                bean {
                    ApplicationRunner {
                        val customerService = ref<CustomerService>()

                        customerService
                            .deleteAll() // first cleanUp Database
                            .thenMany(  // create a list of Customers
                                listOf("John", "Jane", "Jack")
                                    .toFlux()
                                    .map { Customer(name = it) })
                            .flatMap { customerService.save(it) } // Save it to the Database
                            .thenMany(customerService.findAll()) // Search all entries
                            .subscribe { log.info("--> $it") } // subscribe - let`s do the work...
                    }
                }
            }
        }
   }
```
Now we have some data in our MongoDB I think now is time to create a other Bean with the Kotlin DSL that provide a Rest endpoint.
For this we create an `Router` that will provide the following endpoints.
* `/customers` 
* `/customers/{id}`
    
```kotlin
// Rest API
bean {
    router {
        val customerService = ref<CustomerService>()
        GET("/customers") { ok().body(customerService.findAll()) }
        GET("/customers/{id}") { ok().body(customerService.findById(it.pathVariable("id"))) }
    }
}
```
When we start now out application we can call the endpoint and hopefully we get a result like below.

```bash
mvn spring-boot:run
``` 
```bash
http :8080/customers
```

```bash
HTTP/1.1 200 OK
Content-Type: application/json
transfer-encoding: chunked

[
    {
        "id": "a16c9582-0f40-4a7b-a566-372a56c3d5c8",
        "name": "John"
    },
    {
        "id": "944c3752-55c5-4ede-bc09-e02a5e47b390",
        "name": "Jane"
    },
    {
        "id": "478ce3f9-0eff-4018-a056-0656cd2c5ad4",
        "name": "Jack"
    }
]
``` 

Now let's create a sidecar with `Spring cloud Gateway` that provide another Rest API `/api/customers` and `/api/customers/{id}`
Let's create an additional response header `X-AnotherHeader` with the value `SideCar` as well.
 
```kotlin
// Gateway Sidecar API
// http -v :8080/api/customers
// Gateway - Sidecar
bean {
    ref<RouteLocatorBuilder>()
        .routes {
            // http -v :8080/api/customers
            route("sidecar-api") {
                path("/api/**")
                filters {
                    rewritePath("api(?<segment>/?.*)", "/$\\{segment}")
                }
                uri("http://localhost:8080")
            }
        }
}
```
When we call now the EndPoint `/api/customers` we expect that we get the result from before and the additional `ResponsHeader`
with `X-AnotherHeader: SideCar`
```bash
http -v :8080/api/customers
```

```bash
GET /api/customers HTTP/1.1
Accept: */*
Accept-Encoding: gzip, deflate
Connection: keep-alive
Host: localhost:8080
User-Agent: HTTPie/2.0.0

HTTP/1.1 200 OK
Content-Type: application/json
X-AnotherHeader: SideCar
transfer-encoding: chunked

[
    {
        "id": "a36f75ae-a97f-41ba-9b38-59a5a6d38055",
        "name": "John"
    },
    {
        "id": "c0b25559-13ad-4b6b-ae25-adbcd898db82",
        "name": "Jane"
    },
    {
        "id": "2a3a1350-4bea-4504-b732-5b7c97602ebb",
        "name": "Jack"
    }
]
```

With the Kotlin DSL Route definition is it also easy to create routes only for specifics Spring profiles.

The following command will start the application with the `default` profile.
```bash
mvn spring-boot:run
```

`Spring Cloud Gateway` provide with the `Actuator` library an endpoint to check the configured routes.
Also let's check first our Routing Table whe we start the application with the default Spring Profile.

```bash
http :8080/actuator/gateway/routes
```

```bash
HTTP/1.1 200 OK
Content-Type: application/json
transfer-encoding: chunked

[
    {
        "filters": [
            "[[RewritePath api(?<segment>/?.*) = '/${segment}'], order = 0]"
        ],
        "order": 0,
        "predicate": "Paths: [/api/**], match trailing slash: true",
        "route_id": "sidecar-api",
        "uri": "http://localhost:8080"
    }
]
```

Now create a Route just for a specific Spring Profile `foo`

```kotlin
    runApplication<SidecarGatewayApplication>(*args) {
        addInitializers(
            beans {
                // Profile
                profile("foo") {
                    // Gateway - Sidecar
                    bean {
                        ref<RouteLocatorBuilder>()
                            .routes {
                                // http -v :8080/
                                route("sidecar-root-to-customers-api") {
                                    path("/**")
                                    filters {
                                        rewritePath("/(?<segment>/?.*)", "/customers/$\\{segment}")
                                    }
                                    uri("http://localhost:8080/")
                                }
                            }
                    }
                }
            }
        )
}
```


Start the application with the `foo` profile with `-Dspring-boot.run.profiles=foo` and check again the Routing Table.

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=foo
```

```bash
http :8080/actuator/gateway/routes
```

```bash
HTTP/1.1 200 OK
Content-Type: application/json
transfer-encoding: chunked

[
    {
        "filters": [
            "[[RewritePath api(?<segment>/?.*) = '/${segment}'], order = 0]"
        ],
        "order": 0,
        "predicate": "Paths: [/api/**], match trailing slash: true",
        "route_id": "sidecar-api",
        "uri": "http://localhost:8080"
    },
    {
        "filters": [
            "[[RewritePath /(?<segment>/?.*) = '/customers/${segment}'], order = 0]"
        ],
        "order": 0,
        "predicate": "Paths: [/**], match trailing slash: true",
        "route_id": "sidecar-root-to-customers-api",
        "uri": "http://localhost:8080/"
    }
]
```
The example source code can be found here [GitHub kotlin-sidecar-gateway](https://github.com/marzelwidmer/kotlin-sidecar-gateway)