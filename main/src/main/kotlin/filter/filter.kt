package main.filter

data class State(var isMentionOnly: Boolean, var keyword: String)

typealias StateUpdater = (currentState: State) -> State

interface Service {
    fun updateState(stateUpdater: StateUpdater)

    fun toggleMentioned() {
        updateState { State(!it.isMentionOnly, it.keyword) }
    }

    fun changeKeyword(newKeyword: String) {
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
}
