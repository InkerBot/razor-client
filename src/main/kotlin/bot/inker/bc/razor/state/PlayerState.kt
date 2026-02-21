package bot.inker.bc.razor.state

import bot.inker.bc.razor.protocol.account.*
import bot.inker.bc.razor.protocol.common.ItemBundle

data class PlayerState(
    override val id: String,
    override val memberNumber: Int,
    override val name: String,
    override val nickname: String? = null,
    val accountName: String,
    override val creation: Long,
    val money: Int,
    override val owner: String?,
    override val ownership: Ownership?,
    override val lovership: List<Lovership>,
    override val appearance: List<ItemBundle>,
    override val activePose: List<String>,
    val friendList: List<Int>,
    override val whiteList: List<Int>,
    override val blackList: List<Int>,
    val ghostList: List<Int>,
    val skill: List<Skill>,
    override val reputation: List<Reputation>,
    override val difficulty: Difficulty?,
    override val title: String?,
    override val labelColor: String?,
    override val description: String?,
    val rawData: ServerAccountData,
) : CharacterInfo {
    companion object {
        fun fromAccountData(data: ServerAccountData): PlayerState {
            return PlayerState(
                id = data.id,
                memberNumber = data.memberNumber,
                name = data.name,
                nickname = data.nickname,
                accountName = data.accountName ?: "",
                creation = data.creation ?: 0L,
                money = data.money ?: 0,
                owner = data.owner,
                ownership = data.ownership,
                lovership = data.lovership ?: emptyList(),
                appearance = data.appearance ?: emptyList(),
                activePose = data.activePose ?: emptyList(),
                friendList = data.friendList ?: emptyList(),
                whiteList = data.whiteList ?: emptyList(),
                blackList = data.blackList ?: emptyList(),
                ghostList = data.ghostList ?: emptyList(),
                skill = data.skill ?: emptyList(),
                reputation = data.reputation ?: emptyList(),
                difficulty = data.difficulty,
                title = data.title,
                labelColor = data.labelColor,
                description = data.description,
                rawData = data,
            )
        }
    }
}
