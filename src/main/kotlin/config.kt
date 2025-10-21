package com.fairtiq

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.occurrent.application.converter.jackson.JacksonCloudEventConverter
import java.net.URI.create

val mongoUri = "mongodb://admin:secretpass@127.0.0.1:27020/"
val dbName = "occurrent_demo"
val eventCollectionName = "events"


fun createEventConverter(): JacksonCloudEventConverter<DomainEvent> =
    JacksonCloudEventConverter<DomainEvent>(
        createObjectMapper(),
        create("com.fairtiq.guessGame")
    )

fun createObjectMapper(): ObjectMapper = ObjectMapper().apply {
    registerModule(KotlinModule.Builder().build())
}