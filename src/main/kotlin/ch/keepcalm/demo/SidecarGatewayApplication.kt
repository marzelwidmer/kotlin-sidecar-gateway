package ch.keepcalm.demo

import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.cloud.gateway.route.RouteLocator
import org.springframework.cloud.gateway.route.builder.*
import org.springframework.context.annotation.Bean
import org.springframework.context.support.beans
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.server.body
import org.springframework.web.reactive.function.server.router
import reactor.kotlin.core.publisher.toFlux
import java.util.*


@SpringBootApplication
class SidecarGatewayApplication
//{
//    @Bean
//    fun foo(routeLocatorBuilder: RouteLocatorBuilder): RouteLocator = routeLocatorBuilder.routes {
//        route("kotlinFoo") {
//            path("/kotlin/**")
//            filters { stripPrefix(1) }
//            uri("http://httpbin.org")
//        }
//    }
//
//    @Bean
//    fun myRoutes(builder: RouteLocatorBuilder): RouteLocator? {
//        return builder.routes()
//            .route { r: PredicateSpec ->
//                r.path("/api/**")
//                    .filters { f: GatewayFilterSpec ->
//                        f.rewritePath("/api/",
//                            "/service-instances/")
//                    }
//                    .uri("lb://UI/")
//                    .id("first-service")
//            }.build()
//    }
//}
//
//
//        @Bean
//        fun additionalRouteLocator(builder: RouteLocatorBuilder) = builder.routes {
//            route(id = "test-kotlin") {
//                host("kotlin.abc.org") and path("/image/png")
//                filters {
//                    prefixPath("/httpbin")
//                    addResponseHeader("X-TestHeader", "foobar")
//                }
//                uri("http://httpbin.org")
//            }
//        }
//
//}

fun main(args: Array<String>) {
    val log = LoggerFactory.getLogger(SidecarGatewayApplication::class.java)

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
                // Gateway
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

                bean {
                    router {
                        val customerService = ref<CustomerService>()
                        GET("/customers") { ok().body(customerService.findAll()) }
                        GET("/customers/{id}") { ok().body(customerService.findById(it.pathVariable("id"))) }
                    }
                }
            }
        )
    }
}


@Service
class CustomerService(private val customerRepository: CustomerRepository) {
    fun save(customer: Customer) = customerRepository.save(customer)
    fun findAll() = customerRepository.findAll()
    fun deleteAll() = customerRepository.deleteAll()
    fun findById(id: String) = customerRepository.findById(id)
}

interface CustomerRepository : ReactiveCrudRepository<Customer, String>

@Document
data class Customer(@Id val id: String = UUID.randomUUID().toString(), val name: String)
