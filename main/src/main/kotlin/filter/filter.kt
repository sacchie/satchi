package main.filter

data class State(var isMentionOnly: Boolean, var keyword: String)

fun toggleMentioned(updateState: ((currentState: State) -> State) -> Unit) {
    updateState { State(!it.isMentionOnly, it.keyword) }
}

fun changeKeyword(updateState: ((currentState: State) -> State) -> Unit, newKeyword: String) {
    updateState { currentState ->
        newKeyword.trim().let { newTrimmedKeyword ->
            if (newTrimmedKeyword != currentState.keyword) {
                State(currentState.isMentionOnly, newTrimmedKeyword)
            } else {
                currentState
            }
        }
    }
}
