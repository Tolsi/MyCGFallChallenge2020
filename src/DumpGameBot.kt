import java.util.*

fun main() {
    val input = Scanner(System.`in`)

    // game loop
    while (true) {
        val actionCount = input.nextInt()
        (0 until actionCount+2).forEach {
            System.err.println(input.nextLine())
        }
        System.out.println("REST")
    }
}
