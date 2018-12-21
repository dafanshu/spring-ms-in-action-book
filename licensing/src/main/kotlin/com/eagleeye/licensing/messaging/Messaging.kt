package com.eagleeye.licensing.messaging

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import org.springframework.http.MediaType
import org.springframework.util.MimeType
import java.io.IOException

class JackDeserializeK(vc: Class<*>? = null): StdDeserializer<MediaType>(vc) {

    @Throws(IOException::class, JsonProcessingException::class)
    override fun deserialize(jp: JsonParser, ctxt: DeserializationContext): MediaType {
        val node = jp.codec.readTree<JsonNode>(jp)

        return MediaType("application", "json")
    }

}

class JackDeserializeMimeTypeK(vc: Class<*>? = null): StdDeserializer<MimeType>(vc) {

    @Throws(IOException::class, JsonProcessingException::class)
    override fun deserialize(jp: JsonParser, ctxt: DeserializationContext): MimeType {
        val node = jp.codec.readTree<JsonNode>(jp)

        return MimeType("application", "json")
    }
}

data class OrganizationChangeModel(
    val type: String,
    val correlationId: String,
    val action: String,
    val orgId: String
)

