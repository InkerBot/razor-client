package bot.inker.bc.razor.state

import bot.inker.bc.razor.protocol.account.Difficulty
import bot.inker.bc.razor.protocol.account.Lovership
import bot.inker.bc.razor.protocol.account.Ownership
import bot.inker.bc.razor.protocol.account.Reputation
import bot.inker.bc.razor.protocol.common.ItemBundle
import com.google.gson.JsonElement

data class CharacterState(
    override val id: String,
    override val memberNumber: Int,
    override val name: String,
    override val nickname: String? = null,
    override val appearance: List<ItemBundle> = emptyList(),
    override val activePose: List<String> = emptyList(),
    override val owner: String? = null,
    override val ownership: Ownership? = null,
    override val lovership: List<Lovership> = emptyList(),
    override val reputation: List<Reputation> = emptyList(),
    override val whiteList: List<Int> = emptyList(),
    override val blackList: List<Int> = emptyList(),
    override val labelColor: String? = null,
    override val description: String? = null,
    override val title: String? = null,
    override val creation: Long = 0L,
    override val difficulty: Difficulty? = null,
    val arousal: ArousalState? = null,
    val mapData: JsonElement? = null,
    val rawData: JsonElement? = null,
) : CharacterInfo {
    data class ArousalState(
        val orgasmTimer: Long? = null,
        val orgasmCount: Int? = null,
        val progress: Int? = null,
        val progressTimer: Long? = null,
    )
}
