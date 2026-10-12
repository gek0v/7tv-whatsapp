package com.seventv.stickers.core.domain.mapper

object EmoteEmojiMapper {

    private val KEYWORD_MAP: List<Pair<List<String>, List<String>>> = listOf(
        listOf("xd", "kek", "lol", "lmao", "lmfao", "haha", "jaja", "laugh", "lul", "pepelaugh", "joy") to listOf("😂", "🤣", "😆"),
        listOf("sad", "cry", "sob", "depress", "biblethump", "pepehands", "grief", "tear", "alone") to listOf("😭", "😢", "🥺"),
        listOf("love", "heart", "kiss", "beso", "ayaya", "hug", "amor", "cute", "wholesome") to listOf("❤️", "🥰", "😍"),
        listOf("rage", "mad", "angry", "furious", "mald", "enojo", "anger", "tableflip") to listOf("😡", "🤬", "😤"),
        listOf("cat", "gato", "kitty", "neko", "meow") to listOf("🐱", "😺", "😸"),
        listOf("dog", "perro", "doge", "puppy", "woof") to listOf("🐶", "🐕"),
        listOf("frog", "pepe", "pepega", "frogge") to listOf("🐸"),
        listOf("dance", "party", "jam", "rave", "vibe", "disco", "celebrate") to listOf("🎉", "💃", "🕺"),
        listOf("fire", "lit", "burn", "hot", "fuego", "flame") to listOf("🔥", "⚡"),
        listOf("chad", "gigachad", "cool", "ez", "based") to listOf("😎", "🕶️"),
        listOf("think", "hmm", "monkas", "sus", "doubt", "curious") to listOf("🤔", "🧐"),
        listOf("pog", "poggers", "pogchamp", "omg", "wow", "shock", "gasp") to listOf("😮", "😲", "😱"),
        listOf("hi", "bye", "wave", "hola", "adios", "greet", "hello") to listOf("👋"),
        listOf("sleep", "bed", "tired", "zzz", "snore") to listOf("😴", "💤"),
        listOf("pray", "bless", "amen", "please") to listOf("🙏")
    )

    fun mapEmoteNameToEmojis(emoteName: String): List<String> {
        val lower = emoteName.lowercase().trim()
        if (lower.isEmpty()) return listOf("💬")

        val matched = mutableListOf<String>()

        for ((keywords, emojis) in KEYWORD_MAP) {
            if (keywords.any { lower.contains(it) }) {
                for (emoji in emojis) {
                    if (emoji !in matched) {
                        matched.add(emoji)
                        if (matched.size >= 3) break
                    }
                }
            }
            if (matched.size >= 3) break
        }

        return if (matched.isEmpty()) listOf("💬") else matched.take(3)
    }
}
