package com.example.ui.components.audio

import com.example.domain.model.VoiceEffect
import java.util.Locale

enum class AudioSubPanel {
  MAIN_GRID,
  SOUNDS_LIBRARY,
  SOUND_FX,
  RECORD,
  VOICE_ENHANCE,
  VOICE_CHANGER,
  TEXT_TO_AUDIO,
  MUSIC_LIBRARY,
  COPYRIGHT_CHECK
}

data class SoundFxClip(
  val id: String,
  val title: String,
  val category: String,
  val durationMs: Long,
  val icon: String,
  val tags: List<String> = emptyList()
)

object SoundFxCatalog {
  val CATEGORIES = listOf(
    "Trending",
    "Music Clip",
    "Funny",
    "Smooshe",
    "ASMR",
    "Variety Show",
    "Vlog",
    "Comedy",
    "Gaming",
    "Machine",
    "Ding",
    "Cartoon",
    "Cheers",
    "Tense",
    "City",
    "Animals"
  )

  val ALL_CLIPS = listOf(
    // Trending
    SoundFxClip("tr_whoosh_pro", "Cinematic Fast Whoosh", "Trending", 750L, "💨"),
    SoundFxClip("tr_pop_crisp", "Hyper Pop Hit", "Trending", 320L, "🫧"),
    SoundFxClip("tr_sub_boom", "Dramatic Sub Drop Boom", "Trending", 1800L, "💥"),
    SoundFxClip("tr_glitch_swipe", "Futuristic Glitch Swipe", "Trending", 620L, "⚡"),
    SoundFxClip("tr_vinyl_scratch", "DJ Vinyl Stop Scratch", "Trending", 950L, "🎧"),

    // Music Clip
    SoundFxClip("mc_trap_drop", "Trap Horn & Bass Riser", "Music Clip", 2400L, "🎺"),
    SoundFxClip("mc_piano_chime", "Neo Soul Piano Chime", "Music Clip", 2100L, "🎹"),
    SoundFxClip("mc_guitar_lick", "Electric Guitar Strum", "Music Clip", 1600L, "🎸"),
    SoundFxClip("mc_lofi_snare", "Crisp Lo-Fi Snap & Beat", "Music Clip", 1200L, "🥁"),

    // Funny
    SoundFxClip("fn_boing_spring", "Cartoon Boing Spring", "Funny", 680L, "🤪"),
    SoundFxClip("fn_fart_whistle", "Slide Whistle Down", "Funny", 1100L, "🪈"),
    SoundFxClip("fn_quack_honk", "Silly Clown Horn Honk", "Funny", 540L, "📯"),
    SoundFxClip("fn_record_scratch", "Awkward Record Scratch", "Funny", 820L, "🛑"),

    // Smooshe
    SoundFxClip("sm_smoosh_hit", "Velvet Smoosh Impact", "Smooshe", 650L, "☁️"),
    SoundFxClip("sm_plush_drop", "Soft Plush Pillow Land", "Smooshe", 480L, "🧸"),
    SoundFxClip("sm_squish_gel", "Jelly Gelatin Squish", "Smooshe", 720L, "🍮"),
    SoundFxClip("sm_slime_stretch", "Slime Slow Pull Stretch", "Smooshe", 1400L, "🧪"),

    // ASMR
    SoundFxClip("as_whisper_breath", "Gentle Ear Whisper Wind", "ASMR", 2500L, "🤫"),
    SoundFxClip("as_wood_tap", "Fingernail Wood Tapping", "ASMR", 1800L, "🪵"),
    SoundFxClip("as_brush_mic", "Soft Brush Stroke on Mic", "ASMR", 2200L, "🖌️"),
    SoundFxClip("as_paper_crinkle", "Crisp Parchment Crinkle", "ASMR", 1900L, "📜"),

    // Variety Show
    SoundFxClip("vs_drum_roll", "Classic Dramatic Drumroll", "Variety Show", 2600L, "🥁"),
    SoundFxClip("vs_brass_fanfare", "Triumphant Brass Fanfare", "Variety Show", 2100L, "🎺"),
    SoundFxClip("vs_suspense_sting", "Quiz Show Suspense Chime", "Variety Show", 1500L, "❓"),
    SoundFxClip("vs_wrong_buzzer", "Loud Red Cross Buzzer", "Variety Show", 850L, "❌"),

    // Vlog
    SoundFxClip("vl_mouse_click", "Clean Laptop Trackpad Click", "Vlog", 250L, "🖱️"),
    SoundFxClip("vl_page_flip", "Magazine Page Quick Turn", "Vlog", 450L, "📖"),
    SoundFxClip("vl_coffee_pour", "Morning Espresso Pour", "Vlog", 2800L, "☕"),
    SoundFxClip("vl_camera_snap", "Vlog DSLR Shutter Click", "Vlog", 520L, "📸"),

    // Comedy
    SoundFxClip("cm_laugh_track", "Sitcom Audience Giggle", "Comedy", 2400L, "😂"),
    SoundFxClip("cm_rimshot_ba_dum", "Punchline Ba-Dum Tss", "Comedy", 900L, "🥁"),
    SoundFxClip("cm_crickets_chirp", "Awkward Silence Crickets", "Comedy", 2200L, "🦗"),
    SoundFxClip("cm_sad_trombone", "Sad Wah-Wah-Wah Trombone", "Comedy", 1700L, "🎺"),

    // Gaming
    SoundFxClip("gm_level_up", "8-Bit Victory Level Up", "Gaming", 1300L, "🌟"),
    SoundFxClip("gm_coin_collect", "Arcade Golden Coin Ping", "Gaming", 380L, "🪙"),
    SoundFxClip("gm_pixel_laser", "Sci-Fi Pixel Laser Blaster", "Gaming", 420L, "🔫"),
    SoundFxClip("gm_game_over", "Retro 8-Bit Game Over Drop", "Gaming", 1900L, "👾"),

    // Machine
    SoundFxClip("mc_robotic_servo", "High-Tech Robotic Servo", "Machine", 850L, "🤖"),
    SoundFxClip("mc_engine_rev", "Sports Car Turbo Engine Rev", "Machine", 2500L, "🏎️"),
    SoundFxClip("mc_printer_gear", "Mechanical Clockwork Gears", "Machine", 1600L, "⚙️"),
    SoundFxClip("mc_futuristic_door", "Spaceship Pneumatic Door", "Machine", 1100L, "🚀"),

    // Ding
    SoundFxClip("dg_crystal_bell", "Pure Crystal Hotel Bell", "Ding", 750L, "🔔"),
    SoundFxClip("dg_service_bell", "Service Bell Desk Ding", "Ding", 600L, "🛎️"),
    SoundFxClip("dg_toaster_pop", "Toaster Finish Alert Ding", "Ding", 520L, "🍞"),
    SoundFxClip("dg_microwave_done", "Digital Microwave Beep", "Ding", 800L, "📟"),

    // Cartoon
    SoundFxClip("ct_running_feet", "Cartoon Bongo Rapid Feet", "Cartoon", 1400L, "🏃"),
    SoundFxClip("ct_anvil_drop", "Heavy Cartoon Anvil Drop", "Cartoon", 1200L, "🔨"),
    SoundFxClip("ct_slip_banana", "Slipping on Banana Peel", "Cartoon", 780L, "🍌"),
    SoundFxClip("ct_eye_pop", "Surprised Eye Spring Out", "Cartoon", 450L, "👀"),

    // Cheers
    SoundFxClip("ch_stadium_roar", "Huge Stadium Crowd Roar", "Cheers", 3200L, "🏟️"),
    SoundFxClip("ch_party_applause", "Concert Crowd Clapping", "Cheers", 2900L, "👏"),
    SoundFxClip("ch_party_popper", "Confetti Party Cannon Pop", "Cheers", 850L, "🎉"),
    SoundFxClip("ch_kids_yay", "Happy Group Cheering 'Yay!'", "Cheers", 1800L, "🥳"),

    // Tense
    SoundFxClip("ts_heartbeat_slow", "Deep Cinema Heartbeat Thud", "Tense", 2600L, "💓"),
    SoundFxClip("ts_horror_string", "Violin Screech Tension", "Tense", 1900L, "🎻"),
    SoundFxClip("ts_ticking_bomb", "Metallic Ticking Timer Clock", "Tense", 2400L, "⏱️"),
    SoundFxClip("ts_dark_drone", "Ominous Cinematic Low Drone", "Tense", 3500L, "🌑"),

    // City
    SoundFxClip("cty_subway_chime", "Metro Train Door Chime", "City", 1200L, "🚇"),
    SoundFxClip("cty_traffic_ambience", "Busy Downtown Street Horns", "City", 3200L, "🚕"),
    SoundFxClip("cty_rain_umbrella", "Raindrops on Umbrella & Road", "City", 3000L, "🌧️"),
    SoundFxClip("cty_pedestrian_beep", "Walk Sign Electronic Beep", "City", 900L, "🚦"),

    // Animals
    SoundFxClip("an_kitten_meow", "Adorable Kitten Purr & Meow", "Animals", 1100L, "🐱"),
    SoundFxClip("an_happy_dog_bark", "Puppy Excited Bark", "Animals", 850L, "🐶"),
    SoundFxClip("an_birds_forest", "Morning Forest Birds Song", "Animals", 2800L, "🐦"),
    SoundFxClip("an_lion_roar", "Mighty Lion Safari Roar", "Animals", 2100L, "🦁")
  )
}

data class VoiceChangerItem(
  val id: String,
  val name: String,
  val category: String, // "Voice Filters", "Voice Characters", "Speech to Song", "Favourite"
  val icon: String,
  val voiceEffect: VoiceEffect,
  val pitchShift: Float = 0f
)

object VoiceChangerCatalog {
  val FILTERS = listOf(
    VoiceChangerItem("vf_deep", "Deep Voice", "Voice Filters", "👹", VoiceEffect.DEEP_VOICE, -5f),
    VoiceChangerItem("vf_chipmunk", "Chipmunk", "Voice Filters", "🐿️", VoiceEffect.CHIPMUNK, 6f),
    VoiceChangerItem("vf_robot", "Robot", "Voice Filters", "🤖", VoiceEffect.ROBOT, 0f),
    VoiceChangerItem("vf_echo", "Echo Reverb", "Voice Filters", "🏛️", VoiceEffect.ECHO_REVERB, 0f),
    VoiceChangerItem("vf_telephone", "Telephone", "Voice Filters", "☎️", VoiceEffect.TELEPHONE, 0f),
    VoiceChangerItem("vf_radio", "AM Radio", "Voice Filters", "📻", VoiceEffect.RADIO, 0f),
    VoiceChangerItem("vf_megaphone", "Megaphone", "Voice Filters", "📢", VoiceEffect.MEGAPHONE, 0f),
    VoiceChangerItem("vf_anonymous", "Anonymous", "Voice Filters", "🕵️", VoiceEffect.ANONYMOUS, -3f),
    VoiceChangerItem("vf_vocoder", "Vocoder", "Voice Filters", "🎛️", VoiceEffect.VOCODER, 0f),
    VoiceChangerItem("vf_chiptune", "8-Bit Crush", "Voice Filters", "🕹️", VoiceEffect.CHIPTUNE, 0f),
    VoiceChangerItem("vf_disco", "Disco Chorus", "Voice Filters", "🪩", VoiceEffect.DISCO, 2f),
    VoiceChangerItem("vf_cartoon", "Cartoon Bounce", "Voice Filters", "🤪", VoiceEffect.CARTOON, 4f)
  )

  val CHARACTERS = listOf(
    VoiceChangerItem("vc_giant", "Giant Titan", "Voice Characters", "🗿", VoiceEffect.GIANT, -7f),
    VoiceChangerItem("vc_elf", "Tiny Pixie", "Voice Characters", "🧚", VoiceEffect.ELF, 7f),
    VoiceChangerItem("vc_alien", "Alien Invader", "Voice Characters", "👽", VoiceEffect.ALIEN, 3f),
    VoiceChangerItem("vc_villain", "Dark Lord", "Voice Characters", "🦹", VoiceEffect.DEEP_VOICE, -6f),
    VoiceChangerItem("vc_bunny", "Cartoon Bunny", "Voice Characters", "🐰", VoiceEffect.CARTOON, 5f),
    VoiceChangerItem("vc_astronaut", "Astronaut", "Voice Characters", "👨‍🚀", VoiceEffect.RADIO, 0f),
    VoiceChangerItem("vc_detective", "Detective Noir", "Voice Characters", "🕵️‍♂️", VoiceEffect.TELEPHONE, -2f),
    VoiceChangerItem("vc_ghost", "Ghost Whisper", "Voice Characters", "👻", VoiceEffect.ECHO_REVERB, 1f)
  )

  val SPEECH_TO_SONG = listOf(
    VoiceChangerItem("ss_autotune", "AutoTune Pop", "Speech to Song", "🎤", VoiceEffect.AUTOTUNE, 2f),
    VoiceChangerItem("ss_rnb", "R&B Harmonies", "Speech to Song", "🎶", VoiceEffect.SPEECH_TO_SONG, 1f),
    VoiceChangerItem("ss_synthwave", "Retro Synth Melodic", "Speech to Song", "🎹", VoiceEffect.DISCO, 0f),
    VoiceChangerItem("ss_chiptune_song", "8-Bit Arcade Song", "Speech to Song", "🕹️", VoiceEffect.CHIPTUNE, 3f)
  )

  val ALL_EFFECTS = FILTERS + CHARACTERS + SPEECH_TO_SONG
}

data class RoyaltyFreeMusicTrack(
  val id: String,
  val title: String,
  val artist: String,
  val genre: String,
  val durationMs: Long,
  val icon: String
)

object MusicCatalog {
  val TRACKS = listOf(
    RoyaltyFreeMusicTrack("mus_chill_hop", "Midnight Coffee & Study", "Lofi Beats Lab", "Lo-Fi", 34000L, "☕"),
    RoyaltyFreeMusicTrack("mus_vlog_upbeat", "Sunny Day Beach Walk", "Creators Studio", "Vlog", 28000L, "☀️"),
    RoyaltyFreeMusicTrack("mus_epic_trailer", "Rise of the Valkyrie", "Cinematic Soundscapes", "Cinematic", 42000L, "⚔️"),
    RoyaltyFreeMusicTrack("mus_cyber_future", "Neon Highway 2099", "Synthwave Syndicate", "Electronic", 31000L, "🏎️"),
    RoyaltyFreeMusicTrack("mus_acoustic_sunset", "Golden Hour Breeze", "Indie Folk Project", "Acoustic", 29000L, "🎸"),
    RoyaltyFreeMusicTrack("mus_tech_promo", "Modern Minimal Tech", "Future Corporate", "Tech", 25000L, "💻"),
    RoyaltyFreeMusicTrack("mus_ambient_peace", "Morning Light Piano", "Serenity Strings", "Ambient", 38000L, "🎹"),
    RoyaltyFreeMusicTrack("mus_gaming_pulse", "Hyper Pixel Run", "8-Bit Hero", "Gaming", 27000L, "👾")
  )
}

data class TTSVoice(
  val id: String,
  val name: String,
  val subtitle: String,
  val locale: Locale,
  val iconEmoji: String,
  val isAi: Boolean = false,
  val pitch: Float = 1.0f,
  val speed: Float = 1.0f
)

object TTSVoicesCatalog {
  val VOICES = listOf(
    // Urdu Voices
    TTSVoice("ur_male_hamza", "اردو - مردانہ (Hamza)", "Urdu (Pakistan) Natural Male", Locale("ur", "PK"), "🇵🇰", pitch = 0.95f, speed = 0.95f),
    TTSVoice("ur_female_fatima", "اردو - زنانہ (Fatima)", "Urdu (Pakistan) Gentle Female", Locale("ur", "PK"), "🇵🇰", pitch = 1.15f, speed = 0.95f),
    TTSVoice("ur_poetic_narrator", "اردو - صوتی راوی (Iqbal)", "Urdu Poetic Deep Narrator", Locale("ur", "PK"), "🇵🇰", pitch = 0.85f, speed = 0.9f),

    // English Voices
    TTSVoice("en_us_david", "US Male (David)", "American Broadcast English", Locale.US, "🇺🇸", pitch = 0.95f, speed = 1.0f),
    TTSVoice("en_us_sarah", "US Female (Sarah)", "American Warm Friendly", Locale.US, "🇺🇸", pitch = 1.1f, speed = 1.0f),
    TTSVoice("en_uk_arthur", "UK Narrator (Arthur)", "British Documentary Tone", Locale.UK, "🇬🇧", pitch = 0.9f, speed = 0.92f),
    TTSVoice("en_au_liam", "Australian (Liam)", "Australian Casual Story", Locale("en", "AU"), "🇦🇺", pitch = 1.0f, speed = 1.05f),

    // English Characters
    TTSVoice("en_char_anime", "Anime Narrator", "Dynamic Hero Voice", Locale.US, "⚡", pitch = 1.3f, speed = 1.1f),
    TTSVoice("en_char_dj", "Radio DJ Host", "Punchy Energetic Broadcaster", Locale.US, "🎙️", pitch = 0.95f, speed = 1.12f),
    TTSVoice("en_char_grandpa", "Old Storyteller", "Warm Vintage Wisdom", Locale.US, "👴", pitch = 0.8f, speed = 0.85f),
    TTSVoice("en_char_action", "Cinematic Hero", "Deep Dramatic Movie Voice", Locale.US, "🎬", pitch = 0.75f, speed = 0.9f),

    // Other Languages
    TTSVoice("ar_arabic_omar", "العربية (Omar)", "Modern Standard Arabic", Locale("ar"), "🇸🇦", pitch = 1.0f, speed = 1.0f),
    TTSVoice("hi_hindi_aarav", "हिन्दी (Aarav)", "Hindi Natural Voice", Locale("hi", "IN"), "🇮🇳", pitch = 1.0f, speed = 1.0f),
    TTSVoice("es_spanish_sofia", "Español (Sofia)", "Castilian Natural Spanish", Locale("es", "ES"), "🇪🇸", pitch = 1.05f, speed = 1.0f),

    // AI Voices
    TTSVoice("ai_gemini_hyper", "Gemini Neural Natural", "Ultra-High Definition AI Voice", Locale.US, "✨", isAi = true, pitch = 1.0f, speed = 1.0f),
    TTSVoice("ai_whisper_soft", "AI Soft Whisper", "Subtle ASMR Neural Tone", Locale.US, "🤫", isAi = true, pitch = 1.05f, speed = 0.9f),
    TTSVoice("ai_podcast_studio", "Podcast Studio Pro", "Compressed Rich Studio Voice", Locale.US, "🎧", isAi = true, pitch = 0.92f, speed = 0.98f),
    TTSVoice("ai_cinematic_trailer", "AI Cinematic Trailer", "Low Bass Thunder Narration", Locale.US, "🌌", isAi = true, pitch = 0.72f, speed = 0.88f)
  )
}
