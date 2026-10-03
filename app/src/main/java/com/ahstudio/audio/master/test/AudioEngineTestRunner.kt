package com.ahstudio.audio.master.test

import com.ahstudio.audio.master.clips.AudioClipOperations
import com.ahstudio.audio.master.commands.AudioEditCommand
import com.ahstudio.audio.master.commands.AudioUndoRedoAdapter
import com.ahstudio.audio.master.model.AudioClipModel
import com.ahstudio.audio.master.model.AudioSourceModel
import com.ahstudio.audio.master.model.AudioTrackModel
import com.ahstudio.audio.master.model.MasterAudioProject
import com.ahstudio.audio.master.persistence.AudioProjectDeserializer
import com.ahstudio.audio.master.persistence.AudioProjectSerializer
import com.ahstudio.audio.master.timeline.AudioTimelineController

object AudioEngineTestRunner {
    fun runBasicSanityCheck(): Boolean {
        try {
            val src = AudioSourceModel(id = "src1", uri = "/path/test.wav", durationSec = 10.0)
            val clip1 = AudioClipModel(id = "c1", trackId = "t1", sourceId = "src1", timelineStartSec = 0.0, timelineDurationSec = 5.0, sourceStartSec = 0.0, sourceDurationSec = 5.0)
            val track1 = AudioTrackModel(id = "t1", index = 0, clips = listOf(clip1))
            val proj = MasterAudioProject(tracks = listOf(track1), sources = mapOf("src1" to src))

            val undoRedo = AudioUndoRedoAdapter()
            val ctl = AudioTimelineController(undoRedo)
            ctl.setProject(proj)

            val cmd = object : AudioEditCommand {
                override val description = "split c1"
                override fun apply(project: MasterAudioProject): MasterAudioProject {
                    val (split1, split2) = AudioClipOperations.split(clip1, 2.5)!!
                    val tr = project.trackById("t1")!!
                    return project.withTrack(tr.copy(clips = listOf(split1, split2)))
                }
                override fun affectedTrackId(): String = "t1"
            }

            val res = ctl.submit(cmd)
            check(res is com.ahstudio.audio.master.AudioEngineResult.Success<*>)
            check(ctl.current.trackById("t1")!!.clips.size == 2)

            val json = AudioProjectSerializer().serialize(ctl.current)
            val restored = AudioProjectDeserializer().deserialize(json)
            check(restored.tracks.size == 1)
            check(restored.tracks[0].clips.size == 2)

            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }
}
