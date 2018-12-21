package com.eagleeye.zuulsvr.filters

import com.netflix.zuul.ZuulFilter
import com.netflix.zuul.context.RequestContext
import mu.KLogging
import org.springframework.stereotype.Component
import java.util.*

@Component
class TrackingFilter: ZuulFilter() {
    override fun run(): Any? {
        logger.debug("TrackingFilter::run processing incoming request for ${RequestContext.getCurrentContext().request.requestURI}")
        if (FilterUtils.correlationId != null)
            logger.debug("TrackingFilter::run ${FilterUtils.CORRELATION_ID} found in tracking filter: ${FilterUtils.correlationId}")
        else {
            FilterUtils.correlationId = UUID.randomUUID().toString()
            logger.debug("TrackingFilter::run ${FilterUtils.CORRELATION_ID} generated in tracking filter: ${FilterUtils.correlationId}") }
        return null
    }

    override fun shouldFilter() = true

    override fun filterType() = FilterUtils.PRE_FILTER_TYPE

    override fun filterOrder() = 1

    companion object: KLogging()
}

@Component
class ResponseFilter: ZuulFilter() {
    override fun run() =
        RequestContext.getCurrentContext().let { ctx ->
            val correlationId = FilterUtils.correlationId
            logger.debug("ResponseFilter::run adding correlation id to response: $correlationId")
            ctx.response.addHeader(FilterUtils.CORRELATION_ID, correlationId)
        }

    override fun shouldFilter() = true

    override fun filterType() = FilterUtils.POST_FILTER_TYPE

    override fun filterOrder() = 1

    companion object: KLogging()
}


object FilterUtils {

    const val CORRELATION_ID  = "tmx-correlation-id"
    const val AUTH_TOKEN      = "tmx-auth-token"
    const val USER_ID         = "tmx-user-id"
    const val ORG_ID          = "tmx-org-id"
    const val PRE_FILTER_TYPE = "pre"
    const val POST_FILTER_TYPE = "post"
    const val ROUTE_FILTER_TYPE = "route"

    var correlationId
       get() =
            RequestContext.getCurrentContext().let { ctx ->
                if (ctx.request.getHeader(CORRELATION_ID) != null)
                    ctx.request.getHeader(CORRELATION_ID)
                else ctx.zuulRequestHeaders[CORRELATION_ID]
        }

        set(value) = RequestContext.getCurrentContext().let { ctx ->
            ctx.zuulRequestHeaders[CORRELATION_ID] = value
        }
}