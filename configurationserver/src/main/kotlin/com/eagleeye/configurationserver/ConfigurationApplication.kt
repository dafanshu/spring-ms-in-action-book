package com.eagleeye.configurationserver

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.cloud.config.server.EnableConfigServer

@SpringBootApplication
@EnableConfigServer
class ConfigurationApplication

fun main(args: Array<String>) {
    runApplication<ConfigurationApplication>(*args)
}
