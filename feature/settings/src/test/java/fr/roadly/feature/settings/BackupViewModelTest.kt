package fr.roadly.feature.settings

import fr.roadly.core.data.backup.BackupError
import fr.roadly.core.data.backup.BackupRepository
import fr.roadly.core.data.backup.BackupResult
import fr.roadly.core.data.backup.ExportSummary
import fr.roadly.core.data.backup.ImportSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream

@OptIn(ExperimentalCoroutinesApi::class)
class BackupViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private class FakeBackup(
        var exportResult: BackupResult<ExportSummary> = BackupResult.Done(ExportSummary(3, 2)),
        var importResult: BackupResult<ImportSummary> = BackupResult.Done(ImportSummary(3, 2, 1, 0)),
    ) : BackupRepository {
        var exports = 0
        var imports = 0
        var lastOutput: OutputStream? = null
        var lastInput: InputStream? = null
        override suspend fun export(open: () -> OutputStream?): BackupResult<ExportSummary> { exports++; lastOutput = open(); return exportResult }
        override suspend fun import(open: () -> InputStream?): BackupResult<ImportSummary> { imports++; lastInput = open(); return importResult }
    }

    @Test fun `export writes to the chosen file and reports what was written`() = runTest {
        val backup = FakeBackup()
        val vm = BackupViewModel(backup)
        val file = ByteArrayOutputStream()
        assertEquals(BackupUi.Idle, vm.ui.value)
        vm.export(open = { file })
        assertEquals(BackupUi.Working, vm.ui.value)
        advanceUntilIdle()
        assertEquals(BackupUi.Exported(ExportSummary(3, 2)), vm.ui.value)
        assertSame(file, backup.lastOutput)
    }

    @Test fun `a failed export says why and lets the screen remove the empty file`() = runTest {
        val vm = BackupViewModel(FakeBackup(exportResult = BackupResult.Failed(BackupError.TOO_LARGE)))
        var cleaned = 0
        vm.export(open = { ByteArrayOutputStream() }, onFailure = { cleaned++ })
        advanceUntilIdle()
        assertEquals(BackupUi.ExportFailed(BackupError.TOO_LARGE), vm.ui.value)
        assertEquals(1, cleaned)
    }

    @Test fun `a successful export does not remove the file`() = runTest {
        val vm = BackupViewModel(FakeBackup())
        var cleaned = 0
        vm.export(open = { ByteArrayOutputStream() }, onFailure = { cleaned++ })
        advanceUntilIdle()
        assertEquals(0, cleaned)
    }

    @Test fun `import reads the chosen file and reports what was added`() = runTest {
        val backup = FakeBackup()
        val vm = BackupViewModel(backup)
        val file = "{}".byteInputStream()
        vm.import { file }
        advanceUntilIdle()
        assertEquals(BackupUi.Imported(ImportSummary(3, 2, 1, 0)), vm.ui.value)
        assertSame(file, backup.lastInput)
    }

    @Test fun `a refused file is reported as an import failure`() = runTest {
        val vm = BackupViewModel(FakeBackup(importResult = BackupResult.Failed(BackupError.NOT_A_BACKUP)))
        vm.import { "x".byteInputStream() }
        advanceUntilIdle()
        assertEquals(BackupUi.ImportFailed(BackupError.NOT_A_BACKUP), vm.ui.value)
    }

    @Test fun `a second request while one is running is ignored`() = runTest {
        val backup = FakeBackup()
        val vm = BackupViewModel(backup)
        vm.export(open = { ByteArrayOutputStream() })
        vm.export(open = { ByteArrayOutputStream() })
        vm.import { "{}".byteInputStream() }
        advanceUntilIdle()
        assertEquals(1, backup.exports)
        assertEquals(0, backup.imports)
        // Une fois terminée, une nouvelle opération est possible.
        vm.import { "{}".byteInputStream() }
        advanceUntilIdle()
        assertEquals(1, backup.imports)
    }
}
