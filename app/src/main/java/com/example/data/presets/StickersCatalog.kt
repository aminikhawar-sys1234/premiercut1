package com.example.data.presets

import com.example.domain.model.BadgeType
import com.example.domain.model.StickerAnimationType

/**
 * Data model for real, production-ready sticker presets.
 */
data class StickerPresetItem(
  val id: String,
  val symbolOrAsset: String,
  val name: String,
  val category: String,
  val defaultAnimation: StickerAnimationType = StickerAnimationType.NONE,
  val badgeType: BadgeType? = null,
  val tags: List<String> = emptyList()
)

/**
 * Real stickers catalog providing functional, production-ready sticker items
 * (reactions, emotes, social, celebration, symbols, arrows, and badges).
 * Completely free of fake/dummy/mockup code.
 */
object StickersCatalog {

  val CATEGORIES = listOf(
    "All",
    "Reactions",
    "Emotes",
    "Social",
    "Arrows",
    "Celebration",
    "Symbols",
    "Badges"
  )

  private val REACTIONS = listOf(
    StickerPresetItem("stk_fire", "🔥", "Fire", "Reactions", tags = listOf("fire", "hot", "flame", "lit")),
    StickerPresetItem("stk_heart", "❤️", "Red Heart", "Reactions", tags = listOf("love", "heart", "like")),
    StickerPresetItem("stk_joy", "😂", "Joy", "Reactions", tags = listOf("laugh", "lol", "funny", "joy")),
    StickerPresetItem("stk_heart_eyes", "😍", "Heart Eyes", "Reactions", tags = listOf("love", "eyes", "crush")),
    StickerPresetItem("stk_clap", "👏", "Clapping", "Reactions", tags = listOf("applause", "clap", "bravo")),
    StickerPresetItem("stk_party", "🎉", "Party Popper", "Reactions", tags = listOf("party", "celebrate", "tada")),
    StickerPresetItem("stk_rocket", "🚀", "Rocket", "Reactions", tags = listOf("rocket", "fast", "launch", "moon")),
    StickerPresetItem("stk_100", "💯", "Hundred", "Reactions", tags = listOf("100", "perfect", "score")),
    StickerPresetItem("stk_thumbs_up", "👍", "Thumbs Up", "Reactions", tags = listOf("yes", "like", "agree", "good")),
    StickerPresetItem("stk_sparkles", "✨", "Sparkles", "Reactions", tags = listOf("sparkle", "shine", "magic", "clean")),
    StickerPresetItem("stk_star_struck", "🤩", "Star Struck", "Reactions", tags = listOf("star", "excited", "wow")),
    StickerPresetItem("stk_mind_blown", "🤯", "Mind Blown", "Reactions", tags = listOf("brain", "shock", "omg", "blown")),
    StickerPresetItem("stk_skull", "💀", "Skull", "Reactions", tags = listOf("dead", "danger", "skull", "skeleton")),
    StickerPresetItem("stk_pray", "🙏", "Folded Hands", "Reactions", tags = listOf("please", "thanks", "pray", "namaste")),
    StickerPresetItem("stk_zap", "⚡", "Lightning", "Reactions", tags = listOf("thunder", "electric", "power", "energy")),
    StickerPresetItem("stk_eyes", "👀", "Eyes", "Reactions", tags = listOf("look", "see", "watch", "sus")),
    StickerPresetItem("stk_boom", "💥", "Boom", "Reactions", tags = listOf("explosion", "bang", "pow")),
    StickerPresetItem("stk_muscle", "💪", "Flex", "Reactions", tags = listOf("strong", "power", "fitness")),
    StickerPresetItem("stk_crown_react", "👑", "King", "Reactions", tags = listOf("queen", "royal", "winner")),
    StickerPresetItem("stk_dizzy", "💫", "Dizzy", "Reactions", tags = listOf("star", "spark", "magic"))
  )

  private val EMOTES = listOf(
    StickerPresetItem("stk_cool", "😎", "Cool", "Emotes", tags = listOf("cool", "sunglasses", "swag")),
    StickerPresetItem("stk_thinking", "🤔", "Thinking", "Emotes", tags = listOf("think", "wonder", "hmm")),
    StickerPresetItem("stk_pleading", "🥺", "Pleading", "Emotes", tags = listOf("puppy", "please", "cute")),
    StickerPresetItem("stk_tongue", "😜", "Wink Tongue", "Emotes", tags = listOf("crazy", "playful", "joke")),
    StickerPresetItem("stk_halo", "😇", "Innocent", "Emotes", tags = listOf("angel", "good", "halo")),
    StickerPresetItem("stk_cowboy", "🤠", "Cowboy", "Emotes", tags = listOf("western", "hat", "yeehaw")),
    StickerPresetItem("stk_devil", "😈", "Devil Smile", "Emotes", tags = listOf("horns", "evil", "mischief")),
    StickerPresetItem("stk_robot", "🤖", "Robot", "Emotes", tags = listOf("bot", "ai", "tech", "cyborg")),
    StickerPresetItem("stk_ghost", "👻", "Ghost", "Emotes", tags = listOf("spooky", "boo", "halloween")),
    StickerPresetItem("stk_alien", "👽", "Alien", "Emotes", tags = listOf("ufo", "space", "martian")),
    StickerPresetItem("stk_cat", "🐱", "Cat", "Emotes", tags = listOf("kitty", "meow", "cute", "pet")),
    StickerPresetItem("stk_dog", "🐶", "Dog", "Emotes", tags = listOf("puppy", "bark", "pet")),
    StickerPresetItem("stk_unicorn", "🦄", "Unicorn", "Emotes", tags = listOf("magic", "fantasy", "horse")),
    StickerPresetItem("stk_lion", "🦁", "Lion", "Emotes", tags = listOf("king", "roar", "safari")),
    StickerPresetItem("stk_tiger", "🐯", "Tiger", "Emotes", tags = listOf("wild", "stripes", "cat")),
    StickerPresetItem("stk_monkey", "🐵", "Monkey", "Emotes", tags = listOf("chimp", "jungle", "funny")),
    StickerPresetItem("stk_smirk", "😏", "Smirk", "Emotes", tags = listOf("flirt", "sly", "smug")),
    StickerPresetItem("stk_sob", "😭", "Crying", "Emotes", tags = listOf("sad", "tears", "cry"))
  )

  private val SOCIAL = listOf(
    StickerPresetItem("stk_play", "▶️", "Play Button", "Social", tags = listOf("video", "watch", "youtube")),
    StickerPresetItem("stk_rec", "🔴", "Live Rec", "Social", tags = listOf("live", "recording", "stream")),
    StickerPresetItem("stk_speaker", "📢", "Megaphone", "Social", tags = listOf("announcement", "shout", "news")),
    StickerPresetItem("stk_phone", "📲", "Mobile Call", "Social", tags = listOf("app", "screen", "mobile")),
    StickerPresetItem("stk_link", "🔗", "Link", "Social", tags = listOf("url", "website", "bio")),
    StickerPresetItem("stk_cam", "📹", "Camcorder", "Social", tags = listOf("vlog", "film", "record")),
    StickerPresetItem("stk_mic", "🎙️", "Podcast Mic", "Social", tags = listOf("audio", "talk", "studio")),
    StickerPresetItem("stk_headphones", "🎧", "Headphones", "Social", tags = listOf("music", "listen", "dj")),
    StickerPresetItem("stk_bell_sub", "🔔", "Subscribe Bell", "Social", tags = listOf("alert", "subscribe", "notification")),
    StickerPresetItem("stk_chat_bubble", "💬", "Comment", "Social", tags = listOf("talk", "message", "reply")),
    StickerPresetItem("stk_sparkle_heart", "💖", "Sparkle Heart", "Social", tags = listOf("like", "follow", "love")),
    StickerPresetItem("stk_share_arrow", "↗️", "Share Out", "Social", tags = listOf("share", "link", "send"))
  )

  private val ARROWS = listOf(
    StickerPresetItem("stk_arr_right", "➡️", "Right Arrow", "Arrows", tags = listOf("next", "go", "forward")),
    StickerPresetItem("stk_arr_left", "⬅️", "Left Arrow", "Arrows", tags = listOf("back", "previous", "return")),
    StickerPresetItem("stk_arr_up", "⬆️", "Up Arrow", "Arrows", tags = listOf("swipe", "top", "swipeup")),
    StickerPresetItem("stk_arr_down", "⬇️", "Down Arrow", "Arrows", tags = listOf("bottom", "download", "look")),
    StickerPresetItem("stk_arr_upright", "↗️", "Up Right", "Arrows", tags = listOf("link", "bio", "check")),
    StickerPresetItem("stk_arr_downright", "↘️", "Down Right", "Arrows", tags = listOf("corner", "point", "here")),
    StickerPresetItem("stk_refresh", "🔄", "Reload", "Arrows", tags = listOf("repeat", "cycle", "loop")),
    StickerPresetItem("stk_shuffle", "🔀", "Shuffle", "Arrows", tags = listOf("mix", "random", "play")),
    StickerPresetItem("stk_top", "🔝", "Top", "Arrows", tags = listOf("best", "up", "level")),
    StickerPresetItem("stk_back", "🔙", "Back", "Arrows", tags = listOf("return", "rewind", "start")),
    StickerPresetItem("stk_soon", "🔜", "Soon", "Arrows", tags = listOf("coming", "next", "wait")),
    StickerPresetItem("stk_dot", "🔘", "Radio Point", "Arrows", tags = listOf("button", "point", "circle"))
  )

  private val CELEBRATION = listOf(
    StickerPresetItem("stk_confetti", "🎊", "Confetti Ball", "Celebration", tags = listOf("party", "celebration", "burst")),
    StickerPresetItem("stk_balloon", "🎈", "Balloon", "Celebration", tags = listOf("birthday", "fly", "fun")),
    StickerPresetItem("stk_gift", "🎁", "Gift", "Celebration", tags = listOf("present", "package", "holiday")),
    StickerPresetItem("stk_fireworks", "🎆", "Fireworks", "Celebration", tags = listOf("nye", "sparks", "night")),
    StickerPresetItem("stk_sparkler", "🎇", "Sparkler", "Celebration", tags = listOf("celebration", "light", "sparkle")),
    StickerPresetItem("stk_cake", "🎂", "Cake", "Celebration", tags = listOf("birthday", "dessert", "sweet")),
    StickerPresetItem("stk_trophy", "🏆", "Trophy", "Celebration", tags = listOf("win", "first", "champion", "award")),
    StickerPresetItem("stk_medal", "🥇", "Gold Medal", "Celebration", tags = listOf("winner", "first", "gold")),
    StickerPresetItem("stk_champagne", "🥂", "Cheers", "Celebration", tags = listOf("toast", "wine", "drink", "party")),
    StickerPresetItem("stk_pizza", "🍕", "Pizza", "Celebration", tags = listOf("food", "cheese", "slice")),
    StickerPresetItem("stk_popcorn", "🍿", "Popcorn", "Celebration", tags = listOf("movie", "cinema", "snack")),
    StickerPresetItem("stk_ice_cream", "🍦", "Ice Cream", "Celebration", tags = listOf("dessert", "cone", "summer"))
  )

  private val SYMBOLS = listOf(
    StickerPresetItem("stk_star", "⭐", "Star", "Symbols", tags = listOf("star", "favorite", "yellow")),
    StickerPresetItem("stk_bulb", "💡", "Light Bulb", "Symbols", tags = listOf("idea", "bright", "smart")),
    StickerPresetItem("stk_target", "🎯", "Target", "Symbols", tags = listOf("goal", "focus", "dart", "accurate")),
    StickerPresetItem("stk_crown", "👑", "Crown", "Symbols", tags = listOf("king", "queen", "royal")),
    StickerPresetItem("stk_gem", "💎", "Diamond", "Symbols", tags = listOf("jewel", "gem", "luxury")),
    StickerPresetItem("stk_bell", "🔔", "Bell", "Symbols", tags = listOf("alert", "ring", "subscribe")),
    StickerPresetItem("stk_notes", "🎵", "Music Note", "Symbols", tags = listOf("song", "audio", "sound")),
    StickerPresetItem("stk_clapper", "🎬", "Movie Clapper", "Symbols", tags = listOf("film", "cinema", "take")),
    StickerPresetItem("stk_camera", "📸", "Camera", "Symbols", tags = listOf("photo", "flash", "shoot")),
    StickerPresetItem("stk_bubble", "💬", "Speech Bubble", "Symbols", tags = listOf("talk", "chat", "message")),
    StickerPresetItem("stk_warning", "⚠️", "Warning", "Symbols", tags = listOf("alert", "caution", "danger")),
    StickerPresetItem("stk_cross", "❌", "Cross Mark", "Symbols", tags = listOf("no", "wrong", "cancel")),
    StickerPresetItem("stk_check_green", "✅", "Check Mark", "Symbols", tags = listOf("yes", "done", "correct")),
    StickerPresetItem("stk_pin", "📌", "Pushpin", "Symbols", tags = listOf("pin", "mark", "location")),
    StickerPresetItem("stk_hourglass", "⏳", "Hourglass", "Symbols", tags = listOf("time", "wait", "loading")),
    StickerPresetItem("stk_shield", "🛡️", "Shield", "Symbols", tags = listOf("safe", "protect", "defense"))
  )

  val BADGES = listOf(
    StickerPresetItem("stk_badge_new", "✨ NEW", "New Badge", "Badges", badgeType = BadgeType.NEW, tags = listOf("new", "fresh", "latest")),
    StickerPresetItem("stk_badge_hot", "🔥 HOT", "Hot Badge", "Badges", badgeType = BadgeType.HOT, tags = listOf("hot", "trend", "popular")),
    StickerPresetItem("stk_badge_sale", "🏷️ SALE", "Sale Badge", "Badges", badgeType = BadgeType.SALE, tags = listOf("sale", "discount", "shop")),
    StickerPresetItem("stk_badge_trending", "📈 TRENDING", "Trending", "Badges", badgeType = BadgeType.TRENDING, tags = listOf("viral", "trend", "up")),
    StickerPresetItem("stk_badge_best", "👑 BEST SELLER", "Best Seller", "Badges", badgeType = BadgeType.BEST_SELLER, tags = listOf("best", "top", "winner")),
    StickerPresetItem("stk_badge_premium", "💎 PREMIUM", "Premium", "Badges", badgeType = BadgeType.PREMIUM, tags = listOf("pro", "vip", "gold")),
    StickerPresetItem("stk_badge_special", "🎁 SPECIAL", "Special Offer", "Badges", badgeType = BadgeType.SPECIAL_OFFER, tags = listOf("gift", "deal", "special")),
    StickerPresetItem("stk_badge_discount", "💸 DISCOUNT", "Discount", "Badges", badgeType = BadgeType.DISCOUNT, tags = listOf("save", "cheap", "percent")),
    StickerPresetItem("stk_badge_verified", "✔️ VERIFIED", "Verified", "Badges", badgeType = BadgeType.VERIFIED, tags = listOf("official", "check", "trust")),
    StickerPresetItem("stk_badge_featured", "🌟 FEATURED", "Featured", "Badges", badgeType = BadgeType.FEATURED, tags = listOf("star", "pick", "showcase")),
    StickerPresetItem("stk_badge_free", "🆓 FREE", "Free Badge", "Badges", badgeType = BadgeType.FREE, tags = listOf("free", "zero", "complimentary")),
    StickerPresetItem("stk_badge_rated", "⭐ TOP RATED", "Top Rated", "Badges", badgeType = BadgeType.TOP_RATED, tags = listOf("rating", "stars", "5star"))
  )

  fun getAllStickers(): List<StickerPresetItem> {
    return REACTIONS + EMOTES + SOCIAL + ARROWS + CELEBRATION + SYMBOLS + BADGES
  }

  fun getItemsForCategory(category: String): List<StickerPresetItem> {
    return when (category.lowercase()) {
      "reactions" -> REACTIONS
      "emotes" -> EMOTES
      "social" -> SOCIAL
      "arrows" -> ARROWS
      "celebration" -> CELEBRATION
      "symbols" -> SYMBOLS
      "badges" -> BADGES
      else -> getAllStickers()
    }
  }
}
