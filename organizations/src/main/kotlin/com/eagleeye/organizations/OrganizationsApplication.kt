package com.eagleeye.organizations

import com.eagleeye.organizations.messaging.SimpleSourceBean
import mu.KLogging
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.cloud.stream.annotation.EnableBinding
import org.springframework.cloud.stream.messaging.Source
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.*
import java.util.*
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Table

@SpringBootApplication
@EnableBinding(Source::class)
class OrganizationsApplication

fun main(args: Array<String>) {
    runApplication<OrganizationsApplication>(*args)
}

@RestController
@RequestMapping("/v1/organizations/{id}")
class OrganizationsController(private val service: OrganizationService) {

    @GetMapping
    fun getOrganization(@PathVariable("id") organizationId: String): Optional<Organization> {
        fun randomlyRunLong() {

            fun sleep() = Thread.sleep(11000)

            val randInt = Random().nextInt(3)
            if (randInt == 2) sleep()
        }

        logger.debug("OrganizationsController::getOrganization Correlation Id: ${UserContextHolder.context?.correlationId
                ?: ""}")

        return service.getOrganization(organizationId)
    }

    @PutMapping()
    fun updateOrganization(@RequestBody organization: Organization) =
            service.updateOrganization(organization)

    @PostMapping
    fun saveOrganization(@RequestBody organization: Organization) =
            service.saveOrganization(organization)

    @DeleteMapping
    fun deleteOrganization(organizationId: String) = service.deleteOrganization(organizationId)

    companion object : KLogging()
}

@Entity
@Table(name="organizations")
data class Organization(
        @Id
        @Column(name = "organization_id", nullable = false)
        val id: String,
        @Column(name = "name", nullable = false)
        val name: String,
        @Column(name = "contact_name", nullable = false)
        val contactName: String,
        @Column(name = "contact_email", nullable = false)
        val contactEmail: String,
        @Column(name = "contact_phone", nullable = false)
        val contactPhone: String)

@Repository
interface OrganizationRepository: CrudRepository<Organization, String>

@Service
class OrganizationService(private val repo: OrganizationRepository,
                          private val simpleSourceBean: SimpleSourceBean) {


    fun getOrganization(organizationId: String) = repo.findById(organizationId)

    fun saveOrganization(organization: Organization) {
        val orgId = UUID.randomUUID().toString()
        simpleSourceBean.publishOrgChange("SAVE", orgId)
        organization.copy(id = orgId).let { repo.save(it) }
    }

    fun updateOrganization(organization: Organization) = repo.save(organization)

    fun deleteOrganization(organizationId: String) = repo.deleteById(organizationId)
}
