package bot.inker.bc.razor.state

import bot.inker.bc.razor.protocol.account.Difficulty
import bot.inker.bc.razor.protocol.account.Lovership
import bot.inker.bc.razor.protocol.account.Ownership
import bot.inker.bc.razor.protocol.account.Reputation
import bot.inker.bc.razor.protocol.common.ItemBundle

interface CharacterInfo {
    val id: String
    val memberNumber: Int
    val name: String
    val nickname: String?
    val creation: Long
    val appearance: List<ItemBundle>
    val activePose: List<String>
    val owner: String?
    val ownership: Ownership?
    val lovership: List<Lovership>
    val reputation: List<Reputation>
    val difficulty: Difficulty?
    val whiteList: List<Int>
    val blackList: List<Int>
    val labelColor: String?
    val description: String?
    val title: String?

    val displayName: String
        get() {
            val nick = nickname?.trim()?.take(20)
            return if (!nick.isNullOrEmpty()) nick else name
        }
}
