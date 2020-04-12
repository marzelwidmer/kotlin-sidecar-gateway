package ch.keepcalm.demo

import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
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
                // Router
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
