package com.eagleeye.organizations

import mu.KLogging
import org.springframework.stereotype.Component
import javax.servlet.*
import javax.servlet.http.HttpServletRequest

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
    override fun destroy() {
    }

    override fun init(p0: FilterConfig?) {
    }

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
