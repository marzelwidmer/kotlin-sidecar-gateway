package ch.keepcalm.demo

import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder
import org.springframework.cloud.gateway.route.builder.filters
import org.springframework.cloud.gateway.route.builder.routes
import org.springframework.context.annotation.Bean
import org.springframework.context.support.beans
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.server.*
import org.springframework.web.reactive.function.server.RequestPredicates.method
import org.springframework.web.reactive.function.server.RequestPredicates.path
import org.springframework.web.reactive.function.server.RouterFunctions.nest
import org.springframework.web.reactive.function.server.RouterFunctions.route
import reactor.kotlin.core.publisher.toFlux
import java.util.*


@SpringBootApplication
class SidecarGatewayApplication

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

                //  Rest API
                bean {
                    router {
                        "/customers".nest{
                            val customerService = ref<CustomerService>()
                            GET("/") { ok().body(customerService.findAll()) }
                            GET("/{id}") { ok().body(customerService.findById(it.pathVariable("id"))) }
                        }
                    }
                }

                // Gateway - Sidecar
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
