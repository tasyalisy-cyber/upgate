package qa.upgate.diagnostics

import org.junit.jupiter.api.extension.AfterTestExecutionCallback
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback
import org.junit.jupiter.api.extension.ExtensionContext

class ApiDiagnosticsExtension : BeforeTestExecutionCallback, AfterTestExecutionCallback {
    override fun beforeTestExecution(context: ExtensionContext) {
        ApiDiagnostics.clear()
    }

    override fun afterTestExecution(context: ExtensionContext) {
        if (context.executionException.isPresent) {
            println(ApiDiagnostics.dump())
        }
        ApiDiagnostics.clear()
    }
}
