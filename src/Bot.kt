import java.lang.Math.abs
import java.util.*

//region objects definitaion

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
    CAST, OPPONENT_CAST, BREW
}

data class Action(
    val actionId: Int, val actionType: ActionType, val delta0: Int, val delta1: Int, val delta2: Int,
    val delta3: Int, val price: Int, val tomeIndex: Int, val taxCount: Int, val castable: Boolean,
    val repeatable: Boolean
) {
    val deltas: Array<Int> by lazy { arrayOf(delta0, delta1, delta2, delta3) }

    val totalDelta: Int by lazy { delta0 + delta1 + delta2 + delta3 }

    val onlyPositive: Boolean by lazy {
        deltas.all { it >= 0 }
    }

    fun available(player: Player): Boolean {
        // NOTE: all opponents casts are not available even for him for now
        return actionType != ActionType.OPPONENT_CAST &&
                (delta0 > 0 || player.inv0 + delta0 >= 0) &&
                (delta1 > 0 || player.inv1 + delta1 >= 0) &&
                (delta2 > 0 || player.inv2 + delta2 >= 0) &&
                (delta3 > 0 || player.inv3 + delta3 >= 0) &&
                (totalDelta + player.totalInv <= 10) &&
                (actionType != ActionType.CAST || castable)
    }
}

data class Player(val inv0: Int, val inv1: Int, val inv2: Int, val inv3: Int, val score: Int) {
    val totalInv by lazy { inv0 + inv1 + inv2 }
}

data class GameState(val me: Player, val opponent: Player, val actions: List<Action>, val moves: List<Move>)

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
        TODO()
    }

    override fun makeMove(currentState: GameState, m: Move): GameState? {
        val currentStateWithMove = currentState.copy(moves = currentState.moves.plus(m))
        return when (m) {
            Wait ->
                currentStateWithMove
            Rest ->
                currentStateWithMove.copy(actions = currentStateWithMove.actions.map {
                    if (it.actionType == ActionType.CAST) it.copy(
                        castable = true
                    ) else it
                })
            is Play -> {
                currentStateWithMove.copy(me = currentState.me.copy(
                    inv0 = currentState.me.inv0 + m.action.delta0 * m.times,
                    inv1 = currentState.me.inv1 + m.action.delta1 * m.times,
                    inv2 = currentState.me.inv2 + m.action.delta2 * m.times,
                    inv3 = currentState.me.inv3 + m.action.delta3 * m.times,
                    score = currentState.me.score + m.action.price * m.times
                ), actions = currentStateWithMove.actions.minus(m.action))
            }
            else -> TODO()
        }
    }
}

fun main(args: Array<String>) {
    val input = Scanner(System.`in`)

    // game loop
    while (true) {
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
        val me = players[0]
        val opponent = players[1]
        val brews by lazy { actions.filter { it.actionType == ActionType.BREW } }
        val casts by lazy { actions.filter { it.actionType == ActionType.CAST } }
        val currentState = GameState(me, opponent, actions, emptyList())
        //endregion

        // Write an action using println()
        // To debug: System.err.println("Debug messages...");

        val bestAvailableAction =
            brews.filter { it.available(me) }.maxBy { it.price.toFloat() / abs(it.totalDelta) }

        val anyAvailableCast by lazy { casts.find { it.available(me) } }

        val bestAction = bestAvailableAction ?: anyAvailableCast

        // in the first league: BREW <id> | WAIT; later: BREW <id> | CAST <id> [<times>] | LEARN <id> | REST | WAIT
        RealGame.makeMove(currentState, bestAction?.let {
            System.err.println("I will play action $it");
            Play(it)
        } ?: Rest)
    }
}
