package dev.anilbeesetti.nextplayer.feature.playlist.screens.detail

import android.content.Context
import android.net.Uri
import dev.anilbeesetti.nextplayer.core.data.playlist.M3UParser
import dev.anilbeesetti.nextplayer.core.data.repository.fake.FakeMediaRepository
import dev.anilbeesetti.nextplayer.core.domain.ObservePlaylistUseCase
import dev.anilbeesetti.nextplayer.core.model.PlaylistItem
import dev.anilbeesetti.nextplayer.core.model.PlaylistItemRecord
import dev.anilbeesetti.nextplayer.core.model.PlaylistRecord
import dev.anilbeesetti.nextplayer.core.model.PlaylistType
import dev.anilbeesetti.nextplayer.core.model.Video
import dev.anilbeesetti.nextplayer.core.ui.base.DataState
import dev.anilbeesetti.nextplayer.feature.playlist.FakeMediaOperationsService
import dev.anilbeesetti.nextplayer.feature.playlist.FakePlaylistRepository
import dev.anilbeesetti.nextplayer.feature.playlist.FakeSystemService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class PlaylistDetailViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var context: Context
    private lateinit var repository: FakePlaylistRepository
    private lateinit var systemService: FakeSystemService
    private lateinit var mediaRepository: FakeMediaRepository
    private lateinit var mediaOperationsService: FakeMediaOperationsService

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        context = RuntimeEnvironment.getApplication()
        repository = FakePlaylistRepository()
        systemService = FakeSystemService()
        mediaRepository = FakeMediaRepository()
        mediaOperationsService = FakeMediaOperationsService()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun fileRefreshReplacesItemsAndPreservesSavedName() = runTest(dispatcher) {
        val sourceFile = kotlin.io.path.createTempFile(suffix = ".m3u").toFile()
        sourceFile.writeText("#PLAYLIST:New source name\nhttps://media.example/new")
        repository.playlist.value = linkedRecord(Uri.fromFile(sourceFile).toString())
        val viewModel = viewModel()
        try {
            runCurrent()
            viewModel.onAction(PlaylistDetailUiAction.Refresh)
            advanceUntilIdle()

            assertEquals(
                listOf("https://media.example/new"),
                repository.replacementCalls.single().second.map { it.uri },
            )
            val playlist = (viewModel.state.value.playlistDataState as DataState.Success).value
            assertEquals("Saved name", playlist?.name)
            assertFalse(viewModel.state.value.isRefreshing)
            assertEquals(1, systemService.toasts.size)
        } finally {
            sourceFile.delete()
        }
    }

    @Test
    fun failedRefreshKeepsCachedItemsAndSkipsReplacement() = runTest(dispatcher) {
        repository.playlist.value = linkedRecord("file:///missing/list.m3u")
        val viewModel = viewModel()
        runCurrent()

        viewModel.onAction(PlaylistDetailUiAction.Refresh)
        advanceUntilIdle()

        val playlist = (viewModel.state.value.playlistDataState as DataState.Success).value
        assertEquals(listOf("https://media.example/cached"), playlist?.items?.map { it.uri })
        assertTrue(repository.replacementCalls.isEmpty())
        assertFalse(viewModel.state.value.isRefreshing)
        assertEquals(1, systemService.toasts.size)
    }

    @Test
    fun localRefreshIsIgnored() = runTest(dispatcher) {
        repository.playlist.value = linkedRecord(source = null).copy(type = PlaylistType.LOCAL)
        val viewModel = viewModel()
        runCurrent()

        viewModel.onAction(PlaylistDetailUiAction.Refresh)
        advanceUntilIdle()

        assertTrue(repository.replacementCalls.isEmpty())
        assertTrue(systemService.toasts.isEmpty())
    }

    @Test
    fun deleteVideoDeletesFileAndRemovesItFromPlaylist() = runTest(dispatcher) {
        val item = localPlaylistWithVideo()
        val viewModel = viewModel()
        runCurrent()

        viewModel.onAction(PlaylistDetailUiAction.DeleteVideo(item))
        advanceUntilIdle()

        assertEquals(listOf(listOf(Uri.parse(LOCAL_VIDEO_URI))), mediaOperationsService.deleteCalls)
        assertEquals(listOf(7L to LOCAL_VIDEO_URI), repository.removedVideos)
    }

    @Test
    fun cancelledDeleteKeepsVideoInPlaylist() = runTest(dispatcher) {
        val item = localPlaylistWithVideo()
        mediaOperationsService.deleteResult = false
        val viewModel = viewModel()
        runCurrent()

        viewModel.onAction(PlaylistDetailUiAction.DeleteVideo(item))
        advanceUntilIdle()

        assertEquals(1, mediaOperationsService.deleteCalls.size)
        assertTrue(repository.removedVideos.isEmpty())
    }

    private fun localPlaylistWithVideo(): PlaylistItem {
        val video = Video.sample.copy(uriString = LOCAL_VIDEO_URI)
        mediaRepository.videos += video
        mediaRepository.notifyMediaChanged()
        repository.playlist.value = PlaylistRecord(
            id = 7,
            name = "Local",
            type = PlaylistType.LOCAL,
            source = null,
            items = listOf(PlaylistItemRecord(position = 0, uri = LOCAL_VIDEO_URI)),
            lastRefreshedAt = null,
        )
        return PlaylistItem(
            position = 0,
            uri = LOCAL_VIDEO_URI,
            title = null,
            tvgLogo = null,
            duration = 0,
            groupTitle = null,
            video = video,
        )
    }

    private fun viewModel() = PlaylistDetailViewModel(
        observePlaylist = ObservePlaylistUseCase(repository, mediaRepository),
        playlistRepository = repository,
        m3uParser = M3UParser(context, Dispatchers.Unconfined),
        systemService = systemService,
        mediaOperationsService = mediaOperationsService,
        input = PlaylistDetailViewModel.Input(7),
        output = PlaylistDetailViewModel.Output(
            navigateUp = {},
            playPlaylist = { _, _ -> },
        ),
    )

    private fun linkedRecord(source: String?) = PlaylistRecord(
        id = 7,
        name = "Saved name",
        type = PlaylistType.M3U_FILE,
        source = source,
        items = listOf(
            PlaylistItemRecord(
                position = 0,
                uri = "https://media.example/cached",
                title = "Cached",
            ),
        ),
        lastRefreshedAt = 123,
    )

    private companion object {
        const val LOCAL_VIDEO_URI = "content://media/external/video/media/1"
    }
}
