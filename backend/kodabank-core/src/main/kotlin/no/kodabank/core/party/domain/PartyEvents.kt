package no.kodabank.core.party.domain

sealed interface PartyEvent {
    val partyId: String
}

data class PartyRegistered(
    override val partyId: String,
    val tenantId: String,
    val nationalIdHash: String,
    val firstName: String,
    val lastName: String,
    val email: String?,
    val phone: String?,
    val address: String?
) : PartyEvent

data class PartyContactUpdated(
    override val partyId: String,
    val email: String?,
    val phone: String?,
    val address: String?
) : PartyEvent

data class PartyIdentityVerified(
    override val partyId: String,
    val verificationMethod: String,
    val verifiedAt: String,
    val level: String
) : PartyEvent

data class PartyStatusChanged(
    override val partyId: String,
    val oldStatus: String,
    val newStatus: String,
    val reason: String
) : PartyEvent
