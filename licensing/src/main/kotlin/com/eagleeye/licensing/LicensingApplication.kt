package com.eagleeye.licensing

import com.eagleeye.licensing.messaging.JackDeserializeK
import com.eagleeye.licensing.messaging.JackDeserializeMimeTypeK
import com.eagleeye.licensing.messaging.OrganizationChangeModel
import com.eagleeye.licensing.utils.ThreadLocalConcurrencyStrategy
import com.eagleeye.licensing.utils.UserContextHolder
import com.eagleeye.licensing.utils.UserContextInterceptor
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
//import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.module.SimpleModule
import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand
import com.netflix.hystrix.contrib.javanica.annotation.HystrixProperty
import com.netflix.hystrix.strategy.HystrixPlugins
import com.netflix.hystrix.strategy.concurrency.HystrixConcurrencyStrategy
import mu.KLogging
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.cloud.client.circuitbreaker.EnableCircuitBreaker
import org.springframework.cloud.client.loadbalancer.LoadBalanced
import org.springframework.cloud.openfeign.EnableFeignClients
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.cloud.stream.annotation.EnableBinding
import org.springframework.cloud.stream.annotation.StreamListener
import org.springframework.cloud.stream.messaging.Sink
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory
import org.springframework.data.redis.core.HashOperations
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer
import org.springframework.data.redis.serializer.StringRedisSerializer
import org.springframework.data.repository.CrudRepository
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.kafka.annotation.EnableKafka
import org.springframework.kafka.support.DefaultKafkaHeaderMapper
//import org.springframework.kafka.annotation.EnableKafka
//import org.springframework.kafka.support.DefaultKafkaHeaderMapper
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository
import org.springframework.stereotype.Service
import org.springframework.util.MimeType
import org.springframework.web.bind.annotation.*
import org.springframework.web.client.RestTemplate
import java.io.IOException
import java.util.*
import javax.annotation.PostConstruct
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Table
import kotlin.collections.LinkedHashMap

@SpringBootApplication
@EnableFeignClients
@EnableCircuitBreaker
@EnableKafka
@EnableBinding(Sink::class)
class LicensingApplication {

    @Value("\${redis.server}")
    lateinit var redisServer:String

    @Value("\${redis.port}")
    lateinit var redisPort:String

    @LoadBalanced
    @Bean
    fun getRestTemplate(): RestTemplate {
        val rt = RestTemplate()
        rt.interceptors.add(UserContextInterceptor())
        return rt
    }

    @Bean
    fun mapper(): DefaultKafkaHeaderMapper {
        val objectMapper = ObjectMapper()
        val module = SimpleModule()
        module.addDeserializer(MediaType::class.java, JackDeserializeK())
        module.addDeserializer(MimeType::class.java, JackDeserializeMimeTypeK())
        objectMapper.registerModule(module)
        val defaultKafkaHeaderMapper = DefaultKafkaHeaderMapper(objectMapper)
        defaultKafkaHeaderMapper.addTrustedPackages("org.springframework.http")
        defaultKafkaHeaderMapper.addTrustedPackages("org.springframework.kafka")
        defaultKafkaHeaderMapper.addTrustedPackages("org.springframework.kafka.support.DefaultKafkaHeaderMapper")
        defaultKafkaHeaderMapper.addTrustedPackages("org.springframework.util.MimeType")
        defaultKafkaHeaderMapper.addTrustedPackages("org.springframework.http.MediaType")
        defaultKafkaHeaderMapper.addTrustedPackages("org.springframework.util")
        defaultKafkaHeaderMapper.addTrustedPackages("org.springframework.http")
        logger.info("**** Added trusted packages **** ")
        return defaultKafkaHeaderMapper
    }

    @Bean
    fun jedisConnectionFactory() =
        JedisConnectionFactory().apply {
            hostName = redisServer
            port =  redisPort.toInt()
        }

    @Bean
    fun redisTemplate(): RedisTemplate<String, Any> {
        val objectMapper = ObjectMapper()
        objectMapper.registerSubtypes(OrganizationRedis::class.java)
        val t = RedisTemplate<String, Any>()
        t.setConnectionFactory(jedisConnectionFactory())
        t.setDefaultSerializer(GenericJackson2JsonRedisSerializer(objectMapper))

        t.setKeySerializer(StringRedisSerializer())
        t.setHashKeySerializer(GenericJackson2JsonRedisSerializer(objectMapper))
        t.setValueSerializer(GenericJackson2JsonRedisSerializer(objectMapper))
        return t

    }

    @StreamListener(Sink.INPUT)
    fun loggerSink(organizationChangeModel: OrganizationChangeModel) =
        logger.debug("Received organizationChangeModel event " +
                "for org id: ${organizationChangeModel.orgId}, " +
                "correlation id: ${organizationChangeModel.correlationId}," +
                "action: ${organizationChangeModel.action}")

    companion object : KLogging()
}


fun main(args: Array<String>) {
    runApplication<LicensingApplication>(*args)
}

@Configuration
class ThreadLocalConfiguration(@Autowired(required = false) private val hystrixConcurrencyStrategy: HystrixConcurrencyStrategy?) {

    @PostConstruct
    fun init() {
        val hystrixEventNotifier = HystrixPlugins.getInstance().eventNotifier
        val metricsPublisher = HystrixPlugins.getInstance().metricsPublisher
        val propertiesStrategy = HystrixPlugins.getInstance().propertiesStrategy
        val commandExecutionHook = HystrixPlugins.getInstance().commandExecutionHook
        HystrixPlugins.reset()
        HystrixPlugins.getInstance().registerConcurrencyStrategy(ThreadLocalConcurrencyStrategy(hystrixConcurrencyStrategy))
        HystrixPlugins.getInstance().registerEventNotifier(hystrixEventNotifier)
        HystrixPlugins.getInstance().registerMetricsPublisher(metricsPublisher )
        HystrixPlugins.getInstance().registerPropertiesStrategy(propertiesStrategy)
        HystrixPlugins.getInstance().registerCommandExecutionHook(commandExecutionHook)

    }
}

@RestController
@RequestMapping("/v1/organizations/{organizationId}/licenses/")
class LicensesController(
        private val service: LicenseService
) {

    companion object: KLogging()

    @GetMapping("{licenseId}")
    fun getLicense(
            @PathVariable("organizationId") organizationId: String,
            @PathVariable("licenseId") licenseId: String): License {
                logger.debug ("LicensesController::getLicense CorrelationId: ${UserContextHolder.context?.correlationId ?: ""}")
                return service.getLicense(organizationId, licenseId)
    }

    @GetMapping
    fun getLicenses(@PathVariable("organizationId") organizationId: String): List<License> {
        logger.debug ("LicensesController::getLicenses CorrelationId: ${UserContextHolder.context?.correlationId ?: ""}")
        return service.getLicensesByOrganizationId(organizationId)
    }

    @PostMapping
    fun saveLicense(@RequestBody license: License) =
            service.saveLicense(license)
}

@Entity
@Table(name="licenses")
data class License(
        @Id
        @Column(name = "licenseId")
        val licenseId: String = "",
        @Column(name = "organization_id", nullable = false)
        val organizationId: String = "",
        @Transient
        val organizationName: String = "",
        @Transient
        val contactName: String = "",
        @Transient
        val contactPhone: String = "",
        @Transient
        val contactEmail: String = "",
        @Column(name = "product_name", nullable = false)
        val productName: String = "",
        @Column(name  = "license_type", nullable = false)
        val licenseType: String = "",
        @Column(name = "license_max", nullable = false)
        val licenseMax: Int = 0,
        @Column(name = "license_allocated", nullable = false)
        val licenseAllocated: Int = 0,
        @Column(name="comment")
        val comment: String = ""
)

@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.WRAPPER_OBJECT, property = "@class")
data class Organization(
    val id: String,
    val name: String,
    val contactName: String,
    val contactEmail: String,
    val contactPhone: String
)


@JsonTypeInfo(use=JsonTypeInfo.Id.NAME, include=JsonTypeInfo.As.PROPERTY, property="type")
data class OrganizationRedis(
    val id: String,
    val name: String,
    val contactName: String,
    val contactEmail: String,
    val contactPhone: String
)

@Repository
interface LicenseRepository: CrudRepository<License, String> {
    fun findByOrganizationId(organizationId: String): List<License>
    fun findByOrganizationIdAndLicenseId(organizationId: String, licenseId: String): License
}

interface OrganizationRedisRepository {
    fun saveOrganization(organization: OrganizationRedis)
    fun updateOrganization(organization: OrganizationRedis)
    fun deleteOrganization(organizationId: String)
    fun findOrganization(organizationId: String): OrganizationRedis?
}

@Repository
class OrganizationRedisRepositoryImpl(val redisTemplate: RedisTemplate<String, Any>): OrganizationRedisRepository {

    companion object {
        const val HASH_NAME = "organization"
    }

    private lateinit var hashOperations: HashOperations<String, String, Any>

    @PostConstruct
    fun init() { hashOperations =  redisTemplate.opsForHash<String, Any>() }

    override fun updateOrganization(organization: OrganizationRedis) {
        hashOperations.put(HASH_NAME, organization.id, organization)
    }

    override fun deleteOrganization(organizationId: String) {
        hashOperations.delete(HASH_NAME, organizationId)
    }

    override fun findOrganization(organizationId: String) =
        hashOperations.get(HASH_NAME, organizationId)
            ?.let { it as LinkedHashMap<String, String> }
            ?.let {
                OrganizationRedis(
                    id = it["id"] ?: "",
                    name = it["name"] ?: "",
                    contactName = it["contactName"] ?: "",
                    contactEmail = it["contactEmail"] ?: "",
                    contactPhone = it["contactPhone"] ?: "")
            }


    override fun saveOrganization(organization: OrganizationRedis) {
        hashOperations.put(HASH_NAME, organization.id, organization)
    }

}

@Service
open class LicenseService(
        private val repo: LicenseRepository,
        private val serviceConfig: ServiceConfig,
        private val organizationRibbonRestTemplateClient: OrganizationRibbonRestTemplateClient,
        private val organizationFeignClient: OrganizationFeignClient
) {
    companion object: KLogging()

    @HystrixCommand(
        commandProperties = [
            HystrixProperty(
                name = "execution.isolation.thread.timeoutInMilliseconds",
                value = "5000"
            )],
        fallbackMethod = "buildFallbackLicenseList",
        threadPoolKey = "getLicensesByOrganizationId",
        threadPoolProperties = [
            HystrixProperty(name = "coreSize", value = "30"),
            HystrixProperty(name="maxQueueSize", value = "10")
        ]
    )
    fun getLicensesByOrganizationId(organizationId: String): List<License> {
        logger.debug ("LicenseService::getLicenses getLicensesByOrganizationId: ${UserContextHolder.context?.correlationId ?: ""}")
//        randomlyRunLong()
        val licenses = repo.findByOrganizationId(organizationId)
        return licenses
    }

    fun buildFallbackLicenseList(organizationId: String) = listOf(License(productName = "Sorry no licences available"))

    private fun randomlyRunLong() {
        fun sleep() = Thread.sleep(11000)

        val randInt = Random().nextInt(3)
        if (randInt == 2) sleep()
    }

    fun saveLicense(license: License) {
        license.copy(licenseId = UUID.randomUUID().toString()).let { repo.save(it) }
    }

    fun getLicense(organizationId: String, licenseId: String): License {
        val lic =
                repo.findByOrganizationIdAndLicenseId(organizationId, licenseId).let { license ->
                    retrieveOrganization(license.organizationId)?.let { org ->
                        license.copy(
                                organizationName = org.name,
                                contactName = org.contactName,
                                contactPhone = org.contactPhone,
                                contactEmail = org.contactEmail
                        )
                    } ?: license
                }
        return lic
    }

    @HystrixCommand(
        commandProperties = [
            HystrixProperty(
                name = "execution.isolation.thread.timeoutInMilliseconds",
                value = "5000"
            )],
//        fallbackMethod = "buildFallbackLicenseList",
        threadPoolKey = "getLicensesByOrganizationId",
        threadPoolProperties = [
            HystrixProperty(name = "coreSize", value = "30"),
            HystrixProperty(name="maxQueueSize", value = "10")
        ]
    )
    fun retrieveOrganization(organizationId: String): Organization? {
        logger.debug ("LicenseService::retrieveOrganization Correlation id: ${UserContextHolder.context?.correlationId ?: ""}")
//        return organizationFeignClient.getOrganization(organizationId)
        return organizationRibbonRestTemplateClient.getOrganization(organizationId)
    }
}

@Component
class OrganizationRibbonRestTemplateClient(
        private val restTemplate: RestTemplate,
        private val orgRedisRepository: OrganizationRedisRepository){

    @HystrixCommand(commandProperties = [HystrixProperty(
            name = "execution.isolation.thread.timeoutInMilliseconds",
            value = "1000"
    )])
    fun getOrganization(organizationId: String): Organization? {
        fun randomlyRunLong() {
            fun sleep() = Thread.sleep(11000)

            val randInt = Random().nextInt(3)
            if (randInt == 2) sleep()
        }
//        randomlyRunLong()

        val cachedOrg = orgRedisRepository.findOrganization(organizationId)
        logger.debug("retrieved organization from redis cache $cachedOrg")
        return if (cachedOrg == null) {
            logger.debug("organization not found in redis cache for organization id $organizationId")
            logger.debug("retrieving organization from organization service")
            restTemplate.exchange(
                "http://organizationservice/v1/organizations/$organizationId",
                HttpMethod.GET,
                null,
                Organization::class.java).body?.let {
                    orgRedisRepository.saveOrganization(
                        OrganizationRedis(it.id, it.name, it.contactName, it.contactEmail, it.contactPhone))
                    it}
        } else Organization(cachedOrg.id, cachedOrg.name, cachedOrg.contactName, cachedOrg.contactEmail, cachedOrg.contactPhone)
    }

    companion object: KLogging()
}

@Component
@FeignClient("organizationservice")
interface OrganizationFeignClient {

    @RequestMapping(
            method=[ RequestMethod.GET ],
            value=[ "v1/organizations/{organizationId}" ],
            consumes=[ "application/json" ]
    )
    fun getOrganization(@PathVariable organizationId: String): Organization?

}

@Component
class ServiceConfig(@Value("\${example.property}") val exampleProperty: String)