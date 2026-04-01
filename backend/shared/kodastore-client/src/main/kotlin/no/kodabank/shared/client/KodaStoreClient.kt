package no.kodabank.shared.client

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient
import java.time.Instant

@Component
class KodaStoreClient(
    @Value("\${kodastore.url:http://localhost:8080}") baseUrl: String
) {
    private val rest = RestClient.builder()
        .baseUrl(baseUrl)
        .build()

    fun append(
        streamId: String,
        expectedVersion: Int?,
        events: List<NewEventRequest>
    ): List<RecordedEvent> {
        try {
            return rest.post()
                .uri("/api/streams/{streamId}/events", streamId)
                .body(AppendRequest(expectedVersion, events))
                .retrieve()
                .body(Array<RecordedEvent>::class.java)!!
                .toList()
        } catch (e: HttpClientErrorException) {
            if (e.statusCode == HttpStatus.CONFLICT) {
                throw ConcurrencyConflictException(streamId, e.responseBodyAsString)
            }
            throw e
        }
    }

    fun readStream(streamId: String, fromVersion: Int = 0): StreamState {
        return rest.get()
            .uri("/api/streams/{streamId}?fromVersion={v}", streamId, fromVersion)
            .retrieve()
            .body(StreamState::class.java)!!
    }

    fun readAll(fromOffset: Long = 0, limit: Int = 1000): List<RecordedEvent> {
        return rest.get()
            .uri("/api/streams?fromOffset={offset}&limit={limit}", fromOffset, limit)
            .retrieve()
            .body(Array<RecordedEvent>::class.java)!!
            .toList()
    }

    fun readCategory(category: String, fromOffset: Long = 0, limit: Int = 1000): List<RecordedEvent> {
        return rest.get()
            .uri("/api/categories/{category}?fromOffset={offset}&limit={limit}", category, fromOffset, limit)
            .retrieve()
            .body(Array<RecordedEvent>::class.java)!!
            .toList()
    }
}

data class AppendRequest(
    val expectedVersion: Int?,
    val events: List<NewEventRequest>
)

data class NewEventRequest(
    val eventType: String,
    val payload: Map<String, Any?>,
    val metadata: Map<String, Any?> = emptyMap()
)

data class RecordedEvent(
    val globalOffset: Long = 0,
    val streamId: String = "",
    val streamVersion: Int = 0,
    val eventType: String = "",
    val payload: Map<String, Any?> = emptyMap(),
    val metadata: Map<String, Any?> = emptyMap(),
    val createdAt: Instant = Instant.now()
)

data class StreamState(
    val streamId: String = "",
    val version: Int = 0,
    val events: List<RecordedEvent> = emptyList()
)

class ConcurrencyConflictException(streamId: String, detail: String) :
    RuntimeException("Concurrency conflict on $streamId: $detail")
