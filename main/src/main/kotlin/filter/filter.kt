package main.filter

data class State(var isMentionOnly: Boolean)

fun toggleMentioned(updateState: ((currentState: State) -> State) -> Unit) {
    updateState { State(!it.isMentionOnly) }
}
