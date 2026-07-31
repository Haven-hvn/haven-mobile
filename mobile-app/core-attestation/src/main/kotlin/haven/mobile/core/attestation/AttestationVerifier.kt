package haven.mobile.core.attestation

import haven.mobile.core.domain.Attestation
import haven.mobile.core.domain.error.HavenError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

interface AttestationVerifier {
    suspend fun verifySingle(attestation: Attestation, subjectCid: String): Result<Unit>
    suspend fun verifyBatch(attestation: Attestation, subjectCid: String): Result<Unit>
}