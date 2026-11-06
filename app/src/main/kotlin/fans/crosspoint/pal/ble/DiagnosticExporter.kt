package fans.crosspoint.pal.ble

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import androidx.core.content.FileProvider
import java.io.File

/**
 * Produces a shareable URI for the diagnostic trace export. The share sheet
 * (FileProvider + ACTION_SEND) needs no runtime permission, and the file
 * lands wherever the user chooses.
 */
fun interface DiagnosticExporter {

    /** Renders the JSONL export and returns a shareable content URI. */
    fun export(): Uri
}

class FileDiagnosticExporter(private val context: Context, private val trace: SessionDiagnosticTrace) :
    DiagnosticExporter {

    override fun export(): Uri {
        val dir = File(context.cacheDir, EXPORT_DIR)
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, EXPORT_FILE)
        file.writeText(trace.exportJsonl(exportedWallUtc = SessionDiagnosticTrace.utcNow()))
        return FileProvider.getUriForFile(context, AUTHORITY, file)
    }

    private companion object {
        const val EXPORT_DIR = "exports"
        const val EXPORT_FILE = "crosspoint-ble-session.jsonl"
        const val AUTHORITY = "fans.crosspoint.pal.diagnostics"
    }
}
