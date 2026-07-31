package haven.mobile.core.security

import haven.mobile.core.domain.error.HavenError

data class DisconnectReport(
    val steps: List<StepResult>,
    val overallOk: Boolean,
) {
    data class StepResult(
        val name: String,
        val ok: Boolean,
        val errorCode: String?,
    )
}