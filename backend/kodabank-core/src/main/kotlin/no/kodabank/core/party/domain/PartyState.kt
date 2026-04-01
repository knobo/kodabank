package no.kodabank.core.party.domain

enum class PartyStatus { PENDING, ACTIVE, SUSPENDED, CLOSED }

data class PartyState(
    val partyId: String,
    val tenantId: String,
    val nationalIdHash: String,
    val firstName: String,
    val lastName: String,
    val email: String?,
    val phone: String?,
    val address: String?,
    val status: PartyStatus,
    val verificationLevel: String?,
    val version: Int
) {
    val fullName: String get() = "$firstName $lastName"

    companion object {
        val EMPTY = PartyState("", "", "", "", "", null, null, null, PartyStatus.PENDING, null, 0)

        fun evolve(state: PartyState, event: PartyEvent, version: Int): PartyState =
            when (event) {
                is PartyRegistered -> state.copy(
                    partyId = event.partyId,
                    tenantId = event.tenantId,
                    nationalIdHash = event.nationalIdHash,
                    firstName = event.firstName,
                    lastName = event.lastName,
                    email = event.email,
                    phone = event.phone,
                    address = event.address,
                    status = PartyStatus.PENDING,
                    version = version
                )
                is PartyContactUpdated -> state.copy(
                    email = event.email ?: state.email,
                    phone = event.phone ?: state.phone,
                    address = event.address ?: state.address,
                    version = version
                )
                is PartyIdentityVerified -> state.copy(
                    status = PartyStatus.ACTIVE,
                    verificationLevel = event.level,
                    version = version
                )
                is PartyStatusChanged -> state.copy(
                    status = PartyStatus.valueOf(event.newStatus.uppercase()),
                    version = version
                )
            }

        fun rebuild(events: List<Pair<PartyEvent, Int>>): PartyState =
            events.fold(EMPTY) { state, (event, version) -> evolve(state, event, version) }
    }
}
