fun main() {
    println(
        botStep(
            GameState(
                1,
                Player(3, 0, 0, 0, 0),
                Player(3, 0, 0, 0, 0),
                listOf(
                    Action(70, ActionType.BREW, -2, -2, 0, -2, 15, -1, -1, false, false),
                    Action(72, ActionType.BREW, 0, -2, -2, -2, 19, -1, -1, false, false),
                    Action(58, ActionType.BREW, 0, -3, 0, -2, 14, -1, -1, false, false),
                    Action(50, ActionType.BREW, -2, 0, 0, -2, 10, -1, -1, false, false),
                    Action(67, ActionType.BREW, 0, -2, -1, -1, 12, -1, -1, false, false),
                    Action(78, ActionType.CAST, 2, 0, 0, 0, 0, -1, -1, true, false),
                    Action(79, ActionType.CAST, -1, 1, 0, 0, 0, -1, -1, true, false),
                    Action(80, ActionType.CAST, 0, -1, 1, 0, 0, -1, -1, true, false),
                    Action(81, ActionType.CAST, 0, 0, -1, 1, 0, -1, -1, true, false),
                    Action(82, ActionType.OPPONENT_CAST, 2, 0, 0, 0, 0, -1, -1, true, false),
                    Action(83, ActionType.OPPONENT_CAST, -1, 1, 0, 0, 0, -1, -1, true, false),
                    Action(84, ActionType.OPPONENT_CAST, 0, -1, 1, 0, 0, -1, -1, true, false),
                    Action(85, ActionType.OPPONENT_CAST, 0, 0, -1, 1, 0, -1, -1, true, false)
                )
            )
        )
    )
}
