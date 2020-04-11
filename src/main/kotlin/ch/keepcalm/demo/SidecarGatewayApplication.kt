package ch.keepcalm.demo

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class SidecarGatewayApplication

fun main(args: Array<String>) {
	runApplication<SidecarGatewayApplication>(*args)
}
