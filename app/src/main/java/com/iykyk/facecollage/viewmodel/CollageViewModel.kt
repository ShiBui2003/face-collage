package com.iykyk.facecollage.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.iykyk.facecollage.data.ProcessingState
import com.iykyk.facecollage.pipeline.PipelineConfig
import com.iykyk.facecollage.pipeline.VideoPipeline
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Single source of truth for the screen. The pipeline runs in viewModelScope on a background
 * dispatcher, so the UI thread only ever reads the resulting state.
 */
class CollageViewModel(application: Application) : AndroidViewModel(application) {

    private val pipeline = VideoPipeline(application, PipelineConfig())

    private val _state = MutableStateFlow<ProcessingState>(ProcessingState.Idle)
    val state: StateFlow<ProcessingState> = _state.asStateFlow()

    private var running: Job? = null

    fun process(uri: Uri) {
        running?.cancel()
        running = viewModelScope.launch {
            _state.value = ProcessingState.Working(
                stage = ProcessingState.Stage.EXTRACTING,
                label = "Opening your video",
                fraction = 0f,
            )
            try {
                val result = pipeline.run(uri) { progress -> _state.value = progress }
                _state.value = ProcessingState.Done(result)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _state.value = ProcessingState.Failed(e.message ?: "Something went wrong.")
            }
        }
    }

    fun reset() {
        running?.cancel()
        running = null
        _state.value = ProcessingState.Idle
    }
}
