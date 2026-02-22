package bot.inker.bc.razor.protocol.room

enum class ChatRoomSpace(val wireValue: String) {
    FEMALE_ONLY(""),
    MIXED("X"),
    MALE_ONLY("M"),
    ASYLUM("Asylum");

    companion object {
        fun fromWireValue(value: String): ChatRoomSpace {
            return entries.find { it.wireValue == value } ?: FEMALE_ONLY
        }
    }
}

enum class ChatRoomLanguage(val wireValue: String) {
    EN("EN"),
    DE("DE"),
    FR("FR"),
    ES("ES"),
    CN("CN"),
    RU("RU"),
    UA("UA"),
    ANY("");

    companion object {
        fun fromWireValue(value: String): ChatRoomLanguage {
            return entries.find { it.wireValue == value } ?: ANY
        }
    }
}

enum class ChatRoomGame(val wireValue: String) {
    NONE(""),
    CLUB_CARD("ClubCard"),
    LARP("LARP"),
    MAGIC_BATTLE("MagicBattle"),
    GGTS("GGTS"),
    PRISON("Prison");

    companion object {
        fun fromWireValue(value: String): ChatRoomGame {
            return entries.find { it.wireValue == value } ?: NONE
        }
    }
}

enum class ChatRoomAccessRole(val wireValue: String) {
    ALL("All"),
    ADMIN("Admin"),
    WHITELIST("Whitelist");

    companion object {
        fun fromWireValue(value: String): ChatRoomAccessRole {
            return entries.find { it.wireValue == value } ?: ALL
        }
    }
}

enum class ChatRoomRole(val wireValue: String) {
    PLAYER("Player"),
    MISTRESS("Mistress"),
    KIDNAPPER("Kidnapper"),
    DADDY("Daddy"),
    MASTER("Master"),
    LOVER("Lover"),
    OWNER("Owner"),
    FAMILY("Family"),
    WHITELIST("Whitelist");

    companion object {
        fun fromWireValue(value: String): ChatRoomRole {
            return entries.find { it.wireValue == value } ?: PLAYER
        }
    }
}

enum class ChatRoomBlockCategory(val wireValue: String) {
    MEDICAL("Medical"),
    EXTREME("Extreme"),
    PONY("Pony"),
    SCI_FI("SciFi"),
    ABDL("ABDL"),
    FANTASY("Fantasy"),
    SMOKING("Smoking"),
    LEASHING("Leashing"),
    PHOTOS("Photos"),
    AROUSAL("Arousal");

    companion object {
        fun fromWireValue(value: String): ChatRoomBlockCategory? {
            return entries.find { it.wireValue == value }
        }
    }
}

enum class ChatRoomAdminAction(val wireValue: String) {
    UPDATE("Update"),
    MOVE_LEFT("MoveLeft"),
    MOVE_RIGHT("MoveRight"),
    KICK("Kick"),
    BAN("Ban"),
    UNBAN("Unban"),
    PROMOTE("Promote"),
    DEMOTE("Demote"),
    SHUFFLE("Shuffle"),
    SWAP("Swap");

    companion object {
        fun fromWireValue(value: String): ChatRoomAdminAction {
            return entries.find { it.wireValue == value } ?: UPDATE
        }
    }
}
