import java.util.*
import kotlin.collections.ArrayList

//region objects definition
sealed class Move {
    abstract val asText: String
}

object Wait : Move() {
    override val asText: String = "WAIT"
}

object Rest : Move() {
    override val asText: String = "REST"
}

data class Play(val action: Action, val times: Int = 1) : Move() {
    override val asText: kotlin.String
        get() = if (action.actionType == ActionType.CAST) {
            if (action.repeatable) {
                "CAST ${action.actionId} $times"
            } else {
                "CAST ${action.actionId}"
            }
        } else {
            "BREW ${action.actionId}"
        }
}

data class Learn(val action: Action) : Move() {
    override val asText: String = "LEARN ${action.actionId}"
}

enum class ActionType {
    CAST, OPPONENT_CAST, BREW, LEARN
}

data class Action(
    val actionId: Int, val actionType: ActionType, val delta0: Int, val delta1: Int, val delta2: Int,
    val delta3: Int, val price: Int, val tomeIndex: Int, val taxCount: Int, val castable: Boolean,
    val repeatable: Boolean
) {
    val deltas: Array<Int> by lazy { arrayOf(delta0, delta1, delta2, delta3) }

    val totalDelta: Int by lazy { delta0 + delta1 + delta2 + delta3 }

    fun availableTimes(times: Int, player: Player): Boolean {
        return (delta0 * times > 0 || player.inv0 + delta0 * times >= 0) &&
                (delta1 * times > 0 || player.inv1 + delta1 * times >= 0) &&
                (delta2 * times > 0 || player.inv2 + delta2 * times >= 0) &&
                (delta3 * times > 0 || player.inv3 + delta3 * times >= 0) &&
                (totalDelta * times + player.totalInv <= 10)
    }

    fun canBePlayedTimes(player: Player): Int {
        // NOTE: all opponents casts are not available even for him for now
        return if (
            actionType != ActionType.OPPONENT_CAST &&
            (delta0 > 0 || player.inv0 + delta0 >= 0) &&
            (delta1 > 0 || player.inv1 + delta1 >= 0) &&
            (delta2 > 0 || player.inv2 + delta2 >= 0) &&
            (delta3 > 0 || player.inv3 + delta3 >= 0) &&
            (totalDelta + player.totalInv <= 10)
        ) {
            when (actionType) {
                ActionType.CAST -> if (castable) {
                        if (repeatable) {
                            (1..10).filter { availableTimes(it, player) }.max() ?: 0
                        } else 1
                    } else 0
                ActionType.LEARN -> if (player.inv0 - tomeIndex >= 0) 1 else 0
                else -> 1
            }
        } else 0
    }
}

data class Player(val inv0: Int, val inv1: Int, val inv2: Int, val inv3: Int, val score: Int) {
    val totalInv by lazy { inv0 + inv1 + inv2 + inv3 }
    val invsScore by lazy { inv1 + inv2 + inv3 }
    val totalScore by lazy { score + invsScore }
}

data class GameState(val step: Int, val me: Player, val opponent: Player, val actions: List<Action>) {
    val brews by lazy { actions.filter { it.actionType == ActionType.BREW } }
    val casts by lazy { actions.filter { it.actionType == ActionType.CAST } }
}

sealed class Simulation {
    abstract fun makeMove(currentState: GameState, m: Move): GameState?
}

object RealGame : Simulation() {
    override fun makeMove(currentState: GameState, m: Move): GameState? {
        //BREW <id> | WAIT; later: BREW <id> | CAST <id> [<times>] | LEARN <id> | REST | WAIT
        println(m.asText)
        return null
    }

    fun makeTestMove(): Move {
        return botStep(
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
            ), System.currentTimeMillis()
        )
    }
}
//endregion

object FastSimulation : Simulation() {
    fun allowedMoves(currentState: GameState, prevMoves: List<Move>): List<Move> {
        val lastOpt = prevMoves.lastOrNull()
        val moves0 = currentState.actions.flatMap { a ->
            val untilBrew = lastOpt?.let {
                when (it) {
                    is Play -> it.action.actionType == ActionType.BREW
                    else -> false
                }
            } ?: false
            if (untilBrew || prevMoves.size > 7) {
                emptyList()
            } else {
                val times = a.canBePlayedTimes(currentState.me)
                if (times == 1) {
                    if (a.actionType == ActionType.LEARN) {
                        listOf(Learn(a))
                    } else {
                        listOf(Play(a))
                    }
                } else if (times > 1) {
                    (1..times).map { Play(a, it) }
                } else {
                    emptyList()
                }
            }
        }

        val moves1 = if (lastOpt?.let { it == Rest } ?: false) {
            moves0
        } else moves0.plus(Rest)

        return moves1
    }

    override fun makeMove(currentState: GameState, m: Move): GameState? {
        return when (m) {
            Wait ->
                currentState
            Rest ->
                currentState.copy(actions = currentState.actions.map {
                    if (it.actionType == ActionType.CAST) it.copy(
                        castable = true
                    ) else it
                })
            is Learn ->
                currentState.copy(
                    actions = currentState.actions.minus(m.action)
                        .plus(m.action.copy(actionType = ActionType.CAST, castable = true))
                )
            is Play -> {
                val currentStateAfterAction = currentState.copy(
                    me = currentState.me.copy(
                        inv0 = currentState.me.inv0 + m.action.delta0 * m.times,
                        inv1 = currentState.me.inv1 + m.action.delta1 * m.times,
                        inv2 = currentState.me.inv2 + m.action.delta2 * m.times,
                        inv3 = currentState.me.inv3 + m.action.delta3 * m.times,
                        score = currentState.me.score + m.action.price * m.times
                    ), actions = currentState.actions.minus(m.action)
                )
                if (m.action.actionType == ActionType.CAST) {
                    currentStateAfterAction.copy(actions = currentStateAfterAction.actions.plus(m.action.copy(castable = false)))
                } else currentStateAfterAction
            }
            else -> TODO()
        }
    }

    private fun makeAllAllowedMoves(
        gameState: GameState,
        moves: List<Move> = ArrayList()
    ): List<Pair<List<Move>, GameState>> {
        return allowedMoves(gameState, moves).map { moves + it to makeMove(gameState, it)!! }
    }

    var bestFound: Pair<List<Move>, GameState>? = null
    var bestValue: Int = -100000
    var seen: Int = 0

    fun resetState() {
        bestFound = null
        bestValue = -100000
        seen = 0
    }

    fun findBestMove(
        gameState: GameState,
        stepStartMillis: Long,
        maxMillis: Long = 50
    ): Pair<List<Move>, GameState> {
        val q = LinkedList<Pair<List<Move>, GameState>>()
        q.push(Pair(emptyList(), gameState))

        fun checkTime(stepStart: Long): Boolean {
            val stepEnd = System.currentTimeMillis()
            val stepTime = stepEnd - stepStart
            return stepEnd + stepTime > stepStartMillis + maxMillis
        }

        loop@ while (true) {
            val stepStart = System.currentTimeMillis()

            for (i in 0 until 50) {
                val current = q.poll()

                if (current != null) {
                    val nextStates = makeAllAllowedMoves(current.second, current.first)
                    q.addAll(nextStates)

                    val currentScore =
                        (current.second.me.totalScore - gameState.me.score) * 10000 - Math.max(current.first.size, 1)
                    if (currentScore > bestValue) {
                        bestValue = currentScore
                        bestFound = current
                    }
                }
            }
            seen += 50

            if (q.isEmpty() || checkTime(stepStart)) break@loop
        }
        return bestFound!!
    }
}

fun botStep(currentState: GameState, stepStartMillis: Long): Move {
    // TODO small depth!
    val bestAction = FastSimulation.findBestMove(currentState, stepStartMillis, 30)
    return bestAction.first.firstOrNull() ?: Rest
}

fun warmUp(n: Int) {
    for (i in 0..n) {
        val m = RealGame.makeTestMove()
        m.asText
    }
}

fun main(args: Array<String>) {
    val input = Scanner(System.`in`)

    warmUp(10)
    FastSimulation.resetState()

    var step = 0
    // game loop
    while (true) {
        step++;
        //region Read objects
        val actionCount = input.nextInt() // the number of spells and recipes in play
        val stepStart = System.currentTimeMillis()
        val actions: List<Action> = sequence {
            for (i in 0 until actionCount) {
                val actionId = input.nextInt() // the unique ID of this spell or recipe
                val actionType =
                    ActionType.valueOf(input.next()) // in the first league: BREW; later: CAST, OPPONENT_CAST, LEARN, BREW
                val delta0 = input.nextInt() // tier-0 ingredient change
                val delta1 = input.nextInt() // tier-1 ingredient change
                val delta2 = input.nextInt() // tier-2 ingredient change
                val delta3 = input.nextInt() // tier-3 ingredient change
                val price = input.nextInt() // the price in rupees if this is a potion
                val tomeIndex =
                    input.nextInt() // in the first two leagues: always 0; later: the index in the tome if this is a tome spell, equal to the read-ahead tax
                val taxCount =
                    input.nextInt() // in the first two leagues: always 0; later: the amount of taxed tier-0 ingredients you gain from learning this spell
                val castable =
                    input.nextInt() != 0 // in the first league: always 0; later: 1 if this is a castable player spell
                val repeatable =
                    input.nextInt() != 0 // for the first two leagues: always 0; later: 1 if this is a repeatable player spell
                yield(
                    Action(
                        actionId, actionType, delta0, delta1, delta2, delta3, price, tomeIndex, taxCount,
                        castable, repeatable
                    )
                )
            }
        }.toList()
        val players: List<Player> = sequence {
            for (i in 0 until 2) {
                val inv0 = input.nextInt() // tier-0 ingredients in inventory
                val inv1 = input.nextInt()
                val inv2 = input.nextInt()
                val inv3 = input.nextInt()
                val score = input.nextInt() // amount of rupees
                yield(Player(inv0, inv1, inv2, inv3, score))
            }
        }.toList()
        val currentState = GameState(step, players[0], players[1], actions)
        //endregion

        // Write an action using println()
        // To debug: System.err.println("Debug messages...");

        // in the first league: BREW <id> | WAIT; later: BREW <id> | CAST <id> [<times>] | LEARN <id> | REST | WAIT
        RealGame.makeMove(currentState, botStep(currentState, stepStart))

        System.gc()

        val stepTime = System.currentTimeMillis() - stepStart
        System.err.println("Step time: $stepTime, seen: ${FastSimulation.seen}")
        FastSimulation.resetState()
    }
}
