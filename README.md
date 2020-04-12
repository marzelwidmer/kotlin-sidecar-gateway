
# Spring Cloud Gateway as Sidecar

## MongoDB Customer Document 
We start with the `MongoDB` `document` class

```kotlin
@Document
data class Customer(@Id val id: String = UUID.randomUUID().toString(), val name: String)
```

## Spring Customer ReactiveCrudRepository Interface 
Then lets create the `Repository`
```kotlin
interface CustomerRepository : ReactiveCrudRepository<Customer, String>
```

## Spring Service Customer Class
Then lets create the `Service` class for it.
````kotlin
@Service
class CustomerService(private val customerRepository: CustomerRepository) {
    fun save(customer: Customer) = customerRepository.save(customer)
    fun findAll() = customerRepository.findAll()
    fun deleteAll() = customerRepository.deleteAll()
    fun findById(id: String) = customerRepository.findById(id)
}
````

## Initialize Data
On every application start let's clean the `MongoDB` collection and write some new data to it.
We do it with a `Bean` definition with the Kotlin DSL.

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
                            .subscribe { log.info("--> $it") } // subscribe
                    }
                }
            }
        }
   }
```
## Create Rest API
Let's also create two Rest Endpoint with the Kotlin DSL `Bean` definition.
`/customers`  and `/customers/{id}`

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
## Create Gateway Sidecar
Now let's create a sidecar with `Spring cloud Gateway`. 
We create an other Rest API  `/api/customers` and `/api/customers/{id}`
Let's also create an additional response header `X-AnotherHeader` with the value `SideCar`
 
```kotlin
// Gateway Sidecar API
// http -v :8080/api/customers
bean {
    ref<RouteLocatorBuilder>()
        .routes {
            route("customer") {
                path("/api/customers**")
                filters {
                    rewritePath("/api/customers/(?<segment>.*)", "/blog/(?<segment>.*)")
                    stripPrefix(1)
                    addResponseHeader("X-AnotherHeader", "SideCar")
                }
                uri("http://localhost:8080/customers")
            }
        }
}
```

## Call Gateway Route
Now is time to call the `Gateway Route` to check if we get a result also with the additional `ResponsHeader`
`X-AnotherHeader: SideCar`

**Request :**
```bash
http -v :8080/api/customers
```


**Response :**
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

