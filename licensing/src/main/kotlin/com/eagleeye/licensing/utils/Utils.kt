package com.eagleeye.licensing.utils

import com.netflix.hystrix.HystrixThreadPoolKey
import com.netflix.hystrix.strategy.concurrency.HystrixConcurrencyStrategy
import com.netflix.hystrix.strategy.concurrency.HystrixRequestVariable
import com.netflix.hystrix.strategy.concurrency.HystrixRequestVariableLifecycle
import com.netflix.hystrix.strategy.properties.HystrixProperty
import mu.KLogging
import org.springframework.http.HttpRequest
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.http.client.ClientHttpResponse
import org.springframework.stereotype.Component
import java.util.concurrent.BlockingQueue
import java.util.concurrent.Callable
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import javax.servlet.Filter
import javax.servlet.FilterChain
import javax.servlet.ServletRequest
import javax.servlet.ServletResponse
import javax.servlet.http.HttpServletRequest
import java.io.IOException



data class UserContext(
        val correlationId: String?,
        val authToken: String?,
        val userId: String?,
        val orgId: String?
) {
    companion object {
        const val USER_ID = "tmx-user-id"
        const val CORRELATION_ID= "tmx-correlation-id"
        const val ORG_ID = "tmx-org-id"
        const val AUTH_TOKEN = "tmx-auth-token"
    }
}



class UserContextHolder() {

    companion object {
        private val userContext = ThreadLocal<UserContext>()
        var context: UserContext?
            get() = if (userContext.get() == null) {
                        val uc = UserContext(null, null, null, null)
                        userContext.set(uc)
                        uc
                    }
                    else userContext.get()
            set(value) = userContext.set(value)
    }
}

@Component
class UserContextFilter: Filter {

    override fun doFilter(request: ServletRequest, response: ServletResponse, filterChain: FilterChain) {

        UserContextHolder.context =
            UserContext(
                    (request as HttpServletRequest).getHeader(UserContext.CORRELATION_ID),
                     request.getHeader(UserContext.AUTH_TOKEN),
                     request.getHeader(UserContext.USER_ID),
                     request.getHeader(UserContext.ORG_ID))
        logger.debug("UserContextFilter Correlation id: ${UserContextHolder.context?.correlationId ?: ""}")
        filterChain.doFilter(request, response)
    }

    companion object: KLogging()
}

class ThreadLocalConcurrencyStrategy(
        private val existingConcurrencyStrategy: HystrixConcurrencyStrategy?): HystrixConcurrencyStrategy() {

    override fun getBlockingQueue(maxQueueSize: Int): BlockingQueue<Runnable> =
        if (existingConcurrencyStrategy == null)
            super.getBlockingQueue(maxQueueSize)
        else
            existingConcurrencyStrategy.getBlockingQueue(maxQueueSize)

    override fun <T : Any?> getRequestVariable(rv: HystrixRequestVariableLifecycle<T>?): HystrixRequestVariable<T> =
        if (existingConcurrencyStrategy == null)
            super.getRequestVariable(rv)
        else
            existingConcurrencyStrategy.getRequestVariable(rv)

    override fun getThreadPool(threadPoolKey: HystrixThreadPoolKey?, corePoolSize: HystrixProperty<Int>?, maximumPoolSize: HystrixProperty<Int>?, keepAliveTime: HystrixProperty<Int>?, unit: TimeUnit?, workQueue: BlockingQueue<Runnable>?): ThreadPoolExecutor =
        if (existingConcurrencyStrategy == null)
            super.getThreadPool(threadPoolKey, corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue)
        else
            existingConcurrencyStrategy.getThreadPool(threadPoolKey, corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue)

    override fun <T : Any?> wrapCallable(callable: Callable<T>?): Callable<T> =
        if (existingConcurrencyStrategy == null)
            super.wrapCallable(DelegatingUserContextCallable<T>(callable, UserContextHolder.context))
        else
            existingConcurrencyStrategy.wrapCallable(DelegatingUserContextCallable<T>(callable, UserContextHolder.context))
}

class DelegatingUserContextCallable<T>(private val callable: Callable<T>?, private var originalUserContext: UserContext?): Callable<T> {

    override fun call(): T? {
        UserContextHolder.context = originalUserContext

        try {
            return callable?.call()
        } finally {
            UserContextHolder.context = null
            originalUserContext = null
        }
    }
}

class UserContextInterceptor: ClientHttpRequestInterceptor {
    override fun intercept(request: HttpRequest, bytes: ByteArray, execution: ClientHttpRequestExecution): ClientHttpResponse {
        val headers = request.headers
        UserContextHolder.context?.correlationId?.let { correlationId ->
            headers.add(UserContext.CORRELATION_ID, correlationId)
        } ?: throw Exception("No user correlation id in user context.")
        UserContextHolder.context?.authToken?.let { authToken ->
            headers.add(UserContext.AUTH_TOKEN, authToken)
        } ?: headers.add(UserContext.AUTH_TOKEN, "")
        return execution.execute(request, bytes)
    }
}