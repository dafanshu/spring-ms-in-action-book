package com.eagleeye.organizations.messaging

import com.eagleeye.organizations.UserContextHolder
import mu.KLogging
import org.springframework.stereotype.Component
import org.springframework.cloud.stream.messaging.Source;
import org.springframework.messaging.support.MessageBuilder

data class OrganizationChangeModel(
        val type: String,
        val correlationId: String,
        val action: String,
        val orgId: String
)

@Component
class SimpleSourceBean(val source: Source) {

    companion object: KLogging()

    fun publishOrgChange(action: String, orgId: String) {
        logger.debug("Publishing organization event to kafka orgId: $orgId action: $action")
        MessageBuilder
                .withPayload(OrganizationChangeModel(
                        OrganizationChangeModel::class.java.typeName,
                        UserContextHolder.context?.correlationId ?: "",
                        action,
                        orgId))
                .build()
                .let {
                    source.output().send(it)
                }

    }
}