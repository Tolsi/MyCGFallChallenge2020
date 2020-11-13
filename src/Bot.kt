import java.io.InputStream
import java.lang.Math.abs
import java.util.*

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

    fun available(player: Player): Int {
        // NOTE: all opponents casts are not available even for him for now
        return if (
            actionType != ActionType.OPPONENT_CAST &&
            actionType != ActionType.LEARN &&
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
}
//endregion

object FastSimulation : Simulation() {
    fun allowedMoves(currentState: GameState): List<Move> {
        return currentState.actions.map { a ->
            val times = a.available(currentState.me)
            Play(a, times).takeIf { times > 0 }
        }.filterNotNull().plus(Rest)
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
                    currentStateAfterAction.copy(actions = currentState.actions.plus(m.action.copy(castable = false)))
                } else currentStateAfterAction
            }
            else -> TODO()
        }
    }

    private fun makeAllAllowedMoves(gameState: GameState, moves: List<Move> = emptyList()): List<Pair<List<Move>, GameState>> {
        return allowedMoves(gameState).map { moves + it to makeMove(gameState, it)!! }
    }

    fun findBestMove(gameState: GameState, depth: Int): Pair<List<Move>, GameState> {
        val moves =  (0..depth).fold(emptyList<Pair<List<Move>, GameState>>() to listOf(emptyList<Move>() to gameState)) {
                current, i ->
            val nextMoves = current.second.flatMap { makeAllAllowedMoves(it.second, it.first) }
            (if (i > 0) { current.first.plus(current.second) } else current.first) to nextMoves
        }.first
        return moves.maxBy { (it.second.me.score - gameState.me.score) / it.first.size }!!
    }
}

fun botStep(currentState: GameState): Move {
    val bestAction = FastSimulation.findBestMove(currentState, 5)

    return bestAction.first.firstOrNull()?.let {
        System.err.println("I will play move $it");
        it
    } ?: Rest
}

fun main(args: Array<String>) {
    val input = Scanner(System.`in`)

    var step = 0
    // game loop
    while (true) {
        step++;
        //region Read objects
        val actionCount = input.nextInt() // the number of spells and recipes in play
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
        RealGame.makeMove(currentState, botStep(currentState))
    }
}
